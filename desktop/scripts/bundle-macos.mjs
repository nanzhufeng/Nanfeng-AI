import { access, mkdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn, execFileSync } from 'node:child_process';

const desktopRoot = resolve(import.meta.dirname, '..');
const appBundle = resolve(desktopRoot, 'src-tauri/target/release/bundle/macos/南枫 AI Desktop.app');
const dmgDirectory = resolve(desktopRoot, 'src-tauri/target/release/bundle/dmg');
const dmgOutput = resolve(dmgDirectory, 'Nanfeng-AI-macOS.dmg');

async function androidPublicCloudConfig() {
  const raw = await readFile(resolve(desktopRoot, '..', 'local.properties'), 'utf8');
  const values = new Map(raw.split(/\r?\n/u).map(line => {
    const marker = line.indexOf('=');
    return marker < 0 ? ['', ''] : [line.slice(0, marker).trim(), line.slice(marker + 1).trim()];
  }));
  const required = [
    ['NANFENG_DESKTOP_BUNDLED_SUPABASE_URL', 'nanfeng.ai.cloud.url'],
    ['NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY', 'nanfeng.ai.cloud.publishableKey'],
  ];
  const config = Object.fromEntries(required.map(([target, source]) => [target, values.get(source) || '']));
  if (Object.values(config).some(value => !value || /[\r\n]/u.test(value))) {
    throw new Error('本机南枫云公开配置不完整；停止打包，避免生成无法登录的 Desktop 包。');
  }
  return config;
}

function run(command, args, options = {}) {
  return new Promise((resolveRun, reject) => {
    const child = spawn(command, args, { cwd: desktopRoot, stdio: 'inherit', ...options });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolveRun() : reject(new Error(`${command} exited with ${code}`)));
  });
}

const publicCloudConfig = await androidPublicCloudConfig();
await run(process.execPath, ['scripts/build.mjs']);
await run('cargo', ['tauri', 'build', '--bundles', 'app'], {
  env: { ...process.env, ...publicCloudConfig, CARGO_NET_OFFLINE: 'true' },
});
await access(appBundle);
const available = execFileSync('security', ['find-identity', '-v', '-p', 'codesigning'], { encoding: 'utf8' });
const developmentIdentity = available.match(/([A-F0-9]{40}) "Apple Development:/)?.[1];
const signingIdentity = process.env.NANFENG_DESKTOP_SIGNING_IDENTITY || developmentIdentity;
if (!signingIdentity) throw new Error('缺少稳定开发签名；停止覆盖，避免钥匙串授权身份随构建改变。');
await run('codesign', ['--force', '--deep', '--sign', signingIdentity, '--identifier', 'com.nanzhufeng.ai.desktop', appBundle]);
await run('codesign', ['--verify', '--deep', '--strict', '--verbose=2', appBundle]);
await mkdir(dmgDirectory, { recursive: true });
await run('hdiutil', ['create', '-volname', '南枫 AI Desktop', '-srcfolder', appBundle, '-ov', '-format', 'UDZO', dmgOutput]);
await run('hdiutil', ['verify', dmgOutput]);
console.log(`macOS development DMG sealed and verified: ${dmgOutput}`);
