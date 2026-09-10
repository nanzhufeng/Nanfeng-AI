import { access } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn, execFileSync } from 'node:child_process';

const desktopRoot = resolve(import.meta.dirname, '..');
const appBundle = resolve(desktopRoot, 'src-tauri/target/release/bundle/macos/南枫 AI Desktop.app');

function run(command, args, options = {}) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(command, args, { cwd: desktopRoot, stdio: 'inherit', ...options });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolveRun() : reject(new Error(`${command} exited with ${code}`)));
  });
}

await run(process.execPath, ['scripts/build.mjs']);
await run('cargo', ['tauri', 'build', '--bundles', 'app'], {
  env: { ...process.env, CARGO_NET_OFFLINE: 'true' },
});
await access(appBundle);
const available = execFileSync('security', ['find-identity', '-v', '-p', 'codesigning'], { encoding: 'utf8' });
const developmentIdentity = available.match(/([A-F0-9]{40}) "Apple Development:/)?.[1];
const signingIdentity = process.env.NANFENG_DESKTOP_SIGNING_IDENTITY || developmentIdentity;
if (!signingIdentity) throw new Error('缺少稳定开发签名；停止覆盖，避免钥匙串授权身份随构建改变。');
await run('codesign', ['--force', '--deep', '--sign', signingIdentity, '--identifier', 'com.nanzhufeng.ai.desktop', appBundle]);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', appBundle]);
console.log(`macOS development bundle sealed and verified: ${appBundle}`);
