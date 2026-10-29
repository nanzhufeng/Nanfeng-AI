import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { homedir } from 'node:os';
import { createReadStream, constants, existsSync } from 'node:fs';
import { mkdir, copyFile, cp, writeFile, rename } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pruneWindowsAcceptanceOutput, pruneWindowsUserOutput, pruneWindowsReleaseAcceptanceOutput } from './prune-windows-acceptance-output.mjs';
import { loadWindowsPublicCloudConfig } from './windows-public-cloud-config.mjs';

if (process.platform !== 'win32') throw new Error('Windows 原生构建必须在 Windows 上执行。');
const options = new Set(process.argv.slice(2));
for (const option of options) {
  if (!['--acceptance', '--debug', '--no-bundle', '--preserve-output', '--target=x86_64-pc-windows-msvc'].includes(option)) throw new Error(`未知构建选项：${option}`);
}
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const publicCloudConfig = await loadWindowsPublicCloudConfig(root, { required: !options.has('--acceptance') });
Object.assign(process.env, publicCloudConfig);
console.log(Object.keys(publicCloudConfig).length
  ? 'Windows public cloud configuration validated for this build; values are not logged.'
  : 'Windows offline acceptance build: no public cloud configuration available.');
// Cargo accepts an external target directory for low-space or isolated builders. Receipt staging
// must follow the same directory instead of silently reading a stale executable from src-tauri/target.
const cachePolicy = resolve(homedir(), '.codex/tools/build-cache-policy/guard.py');
const cachePolicyPython = resolve(homedir(), 'AppData/Local/Programs/Python/Python313/python.exe');
const hasCachePolicy = existsSync(cachePolicy) && existsSync(cachePolicyPython);
function cacheBudget(mode) {
  const result = spawnSync(cachePolicyPython, [cachePolicy, mode, '--project', root], { cwd: root, stdio: 'inherit', windowsHide: true, timeout: 600000 });
  return result.error ? 3 : result.status ?? 3;
}
let configuredTarget = process.env.CARGO_TARGET_DIR;
if (!configuredTarget && hasCachePolicy) {
  const target = spawnSync(cachePolicyPython, [cachePolicy, 'target', '--project', root], { encoding: 'utf8', windowsHide: true, timeout: 15000 });
  if (target.status === 0) configuredTarget = target.stdout.trim();
}
const cargoTargetRoot = resolve(root, configuredTarget || 'src-tauri/target');
process.env.CARGO_TARGET_DIR = cargoTargetRoot;
Object.assign(process.env, { CARGO_INCREMENTAL: '0', CARGO_PROFILE_DEV_DEBUG: '0', CARGO_PROFILE_TEST_DEBUG: '0', CARGO_PROFILE_DEV_INCREMENTAL: 'false', CARGO_PROFILE_TEST_INCREMENTAL: 'false' });
if (hasCachePolicy) {
  if (cacheBudget('preflight') !== 0) throw new Error('编译缓存超出预算或检查失败，已停止新构建；请查看本机 build-cache-policy/latest-report.json。');
  process.once('exit', () => {
    if (cacheBudget('post') !== 0) console.warn('编译缓存仍超预算或正在使用，已保留在用文件；请查看本机缓存策略报告。');
  });
}
const windowsOutputRoot = resolve(root, process.env.NANFENG_AI_WINDOWS_OUTPUT_DIR || 'output');
const buildProfile = options.has('--debug') ? 'debug' : 'release';
const windowsBuiltExecutable = resolve(
  cargoTargetRoot,
  `x86_64-pc-windows-msvc/${buildProfile}/nanfeng-ai-desktop-spike.exe`,
);
function run(script, args = []) {
  const child = spawnSync(process.execPath, [script, ...args], { cwd: root, stdio: 'inherit', shell: false, windowsHide: true });
  if (child.error) throw child.error;
  if (child.status !== 0) process.exit(child.status ?? 1);
}
run(resolve(root, 'scripts/build.mjs'));
run(resolve(root, 'scripts/prepare-windows-media-tools.mjs'));
const args = ['build', '--ci'];
if (options.has('--acceptance')) args.push('--config', 'src-tauri/tauri.windows-acceptance.conf.json');
if (options.has('--debug')) args.push('--debug');
if (options.has('--no-bundle')) args.push('--no-bundle');
// The Windows package is x64. Pin Cargo and receipt staging to the same target
// even for the default bundle command, which does not pass an explicit target.
args.push('--target', 'x86_64-pc-windows-msvc');
args.push('--', '--locked');
run(resolve(root, 'node_modules/@tauri-apps/cli/tauri.js'), args);

if (options.has('--acceptance') && options.has('--no-bundle')
    && options.has('--target=x86_64-pc-windows-msvc')) {
  const source = windowsBuiltExecutable;
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(source)) hash.update(chunk);
  const sha256 = hash.digest('hex').toUpperCase();
  const output = resolve(windowsOutputRoot, buildProfile === 'release' ? 'windows-acceptance-release' : 'windows-acceptance');
  const directory = resolve(output, sha256);
  await mkdir(directory, { recursive: true });
  const executable = resolve(directory, 'NanfengAI-Windows-Acceptance.exe');
  await copyFile(source, executable, constants.COPYFILE_EXCL).catch(error => {
    if (error.code !== 'EEXIST') throw error;
  });
  const mediaTools = resolve(directory, 'media-tools/windows-x86_64');
  await cp(resolve(root, 'src-tauri/resources/media-tools/windows-x86_64'), mediaTools, {
    recursive: true,
    force: true,
  });
  const stagedHash = createHash('sha256');
  for await (const chunk of createReadStream(executable)) stagedHash.update(chunk);
  if (stagedHash.digest('hex').toUpperCase() !== sha256) throw new Error('Acceptance staged executable hash mismatch');
  const pendingReceipt = resolve(output, `current.${process.pid}.pending.json`);
  await writeFile(pendingReceipt, JSON.stringify({
    identifier: 'com.nanzhufeng.ai.desktop.windowsacceptance', sha256, executable, mediaTools,
    build: buildProfile === 'release' ? 'windows-x64-isolated-release-acceptance' : 'windows-x64-isolated-acceptance',
    buildProfile, createdAt: new Date().toISOString(),
  }, null, 2));
  await rename(pendingReceipt, resolve(output, 'current.json'));
  try {
    const retention = options.has('--preserve-output')
      ? { preserved: true }
      : await (buildProfile === 'release' ? pruneWindowsReleaseAcceptanceOutput : pruneWindowsAcceptanceOutput)(windowsOutputRoot, sha256);
    console.log(`Windows acceptance retention: ${JSON.stringify(retention)}`);
  } catch (error) {
    console.warn(`Windows acceptance cleanup deferred: ${error.message}`);
  }
  console.log('Windows isolated acceptance candidate staged with an immutable receipt.');
}

// Daily-use launchers follow this production-only receipt, never the mutable
// target output that may subsequently contain an isolated acceptance binary.
if (!options.has('--acceptance')) {
  const source = windowsBuiltExecutable;
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(source)) hash.update(chunk);
  const sha256 = hash.digest('hex').toUpperCase();
  const output = resolve(windowsOutputRoot, 'windows-user');
  const directory = resolve(output, sha256);
  await mkdir(directory, { recursive: true });
  const executable = resolve(directory, 'NanfengAI.exe');
  await copyFile(source, executable, constants.COPYFILE_EXCL).catch(error => {
    if (error.code !== 'EEXIST') throw error;
  });
  const mediaTools = resolve(directory, 'media-tools/windows-x86_64');
  await cp(resolve(root, 'src-tauri/resources/media-tools/windows-x86_64'), mediaTools, {
    recursive: true,
    force: true,
  });
  const stagedHash = createHash('sha256');
  for await (const chunk of createReadStream(executable)) stagedHash.update(chunk);
  if (stagedHash.digest('hex').toUpperCase() !== sha256) throw new Error('Daily-use staged executable hash mismatch');
  const pendingReceipt = resolve(output, `current.${process.pid}.pending.json`);
  await writeFile(pendingReceipt, JSON.stringify({
    identifier: 'com.nanzhufeng.ai.desktop', sha256, executable, mediaTools,
    build: buildProfile === 'release' ? 'windows-x64-release' : 'windows-x64-development',
    buildProfile, createdAt: new Date().toISOString(),
  }, null, 2));
  await rename(pendingReceipt, resolve(output, 'current.json'));
  try {
    const retention = options.has('--preserve-output')
      ? { preserved: true }
      : await pruneWindowsUserOutput(windowsOutputRoot, sha256);
    console.log(`Windows user build retention: ${JSON.stringify(retention)}`);
  } catch (error) {
    console.warn(`Windows user build cleanup deferred: ${error.message}`);
  }
  console.log('Windows daily-use candidate staged; persistent user data is unchanged.');
}
