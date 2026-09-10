import { access, cp, mkdtemp, readFile, rename, stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { basename, join, resolve } from 'node:path';

const desktopRoot = resolve(import.meta.dirname, '..');
const sourceBundle = resolve(desktopRoot, 'src-tauri/target/release/bundle/macos/南枫 AI Desktop.app');
const sourceExecutable = join(sourceBundle, 'Contents/MacOS/nanfeng-ai-desktop-spike');
const acceptanceBundleName = '南枫 AI C02-C06 验收.app';
const acceptanceExecutable = 'nanfeng-ai-desktop-c02-c06-acceptance';
const acceptanceIdentifier = `com.nanzhufeng.ai.desktop.c02c06acceptance.${process.pid}.${Date.now().toString(36)}`;

function run(command, args) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(command, args, { stdio: 'inherit' });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolveRun() : reject(new Error(`${command} exited with ${code}`)));
  });
}

async function sha256(path) {
  return new Promise((resolveHash, reject) => {
    let output = '';
    const child = spawn('shasum', ['-a', '256', path]);
    child.stdout.on('data', chunk => { output += chunk; });
    child.once('error', reject);
    child.once('exit', code => code === 0
      ? resolveHash(output.trim().split(/\s+/)[0])
      : reject(new Error(`shasum exited with ${code}`)));
  });
}

await Promise.all([access(sourceBundle), access(sourceExecutable)]);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', sourceBundle]);
const sourceExecutableHash = await sha256(sourceExecutable);

const acceptanceParent = await mkdtemp('/tmp/nanfeng-ai-desktop-c02-c06-bundle.');
const acceptanceRoot = await mkdtemp('/tmp/nanfeng-ai-desktop-c02-c06-acceptance.');
const acceptanceBundle = join(acceptanceParent, acceptanceBundleName);
const acceptanceMacOs = join(acceptanceBundle, 'Contents/MacOS');
const acceptanceInfoPlist = join(acceptanceBundle, 'Contents/Info.plist');
const copiedExecutable = join(acceptanceMacOs, basename(sourceExecutable));
const acceptanceExecutablePath = join(acceptanceMacOs, acceptanceExecutable);

await cp(sourceBundle, acceptanceBundle, { recursive: true, force: false });
await rename(copiedExecutable, acceptanceExecutablePath);
await run('/usr/libexec/PlistBuddy', ['-c', `Set :CFBundleIdentifier ${acceptanceIdentifier}`, acceptanceInfoPlist]);
await run('/usr/libexec/PlistBuddy', ['-c', `Set :CFBundleExecutable ${acceptanceExecutable}`, acceptanceInfoPlist]);
await run('plutil', ['-lint', acceptanceInfoPlist]);
await run('codesign', ['--force', '--deep', '--sign', '-', acceptanceBundle]);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', acceptanceBundle]);

const sourceExecutableHashAfter = await sha256(sourceExecutable);
const signedAcceptanceExecutableHash = await sha256(acceptanceExecutablePath);
const [bundleStats, executableStats] = await Promise.all([
  stat(acceptanceBundle),
  stat(acceptanceExecutablePath),
]);
if (!bundleStats.isDirectory() || !executableStats.isFile() || sourceExecutableHash !== sourceExecutableHashAfter) {
  throw new Error('C02-C06 acceptance bundle isolation verification failed');
}

const info = await readFile(acceptanceInfoPlist, 'utf8');
if (!info.includes(acceptanceIdentifier) || !info.includes(acceptanceExecutable)) {
  throw new Error('C02-C06 acceptance bundle identity verification failed');
}

console.log(JSON.stringify({
  acceptanceBundle,
  acceptanceExecutable: acceptanceExecutablePath,
  acceptanceIdentifier,
  acceptanceRoot,
  sourceBundle,
  sourceExecutableHash,
  sourceExecutableHashAfter,
  signedAcceptanceExecutableHash,
}, null, 2));
