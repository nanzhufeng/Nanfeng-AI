import { access } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';

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
await run('codesign', ['--force', '--deep', '--sign', '-', appBundle]);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', appBundle]);
console.log(`macOS development bundle sealed and verified: ${appBundle}`);
