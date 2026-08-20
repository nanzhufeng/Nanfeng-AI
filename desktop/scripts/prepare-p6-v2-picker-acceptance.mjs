import { access, cp, mkdtemp, readFile, rename, stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { tmpdir } from 'node:os';
import { basename, join, resolve } from 'node:path';

const desktopRoot = resolve(import.meta.dirname, '..');
const sourceBundle = resolve(desktopRoot, 'src-tauri/target/release/bundle/macos/南枫 AI Desktop.app');
const sourceExecutable = join(sourceBundle, 'Contents/MacOS/nanfeng-ai-desktop-spike');
const acceptanceBundleName = '南枫 AI P6 v2 Picker 验收.app';
const acceptanceExecutable = 'nanfeng-ai-p6-v2-picker-acceptance';
const acceptanceIdentifier = `com.nanzhufeng.ai.desktop.p6v2pickeracceptance.${process.pid}.${Date.now().toString(36)}`;

function run(command, args, options = {}) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
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

await access(sourceBundle);
await access(sourceExecutable);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', sourceBundle]);
const sourceExecutableHash = await sha256(sourceExecutable);

const acceptanceParent = await mkdtemp(join(tmpdir(), 'nanfeng-ai-p6-v2-picker-acceptance-bundle.'));
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
const [bundleStats, executableStats] = await Promise.all([stat(acceptanceBundle), stat(acceptanceExecutablePath)]);
if (!bundleStats.isDirectory() || !executableStats.isFile() || sourceExecutableHash !== sourceExecutableHashAfter) {
  throw new Error('P6 v2 picker acceptance bundle isolation verification failed');
}

console.log(JSON.stringify({
  acceptanceBundle,
  acceptanceExecutable: acceptanceExecutablePath,
  acceptanceIdentifier,
  launchEnvironment: 'NANFENG_AI_P6_V2_PICKER_ACCEPTANCE_ROOT=/tmp/nanfeng-ai-p6-v2-picker-acceptance.<unique>',
  sourceBundle,
  sourceExecutableHash,
  sourceExecutableHashAfter,
  signedAcceptanceExecutableHash,
}, null, 2));
