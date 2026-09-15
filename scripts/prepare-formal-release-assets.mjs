import { copyFile, mkdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const destination = process.env.NANFENG_RELEASE_DIR;
if (!destination) throw new Error('必须通过 NANFENG_RELEASE_DIR 指定 GitHub 发布文件夹。');

const gradle = await readFile(resolve(root, 'app/build.gradle.kts'), 'utf8');
const androidVersion = gradle.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/u)?.[1];
const desktopConfig = JSON.parse(await readFile(resolve(root, 'desktop/src-tauri/tauri.conf.json'), 'utf8'));
const desktopVersion = desktopConfig.version;
if (!androidVersion || androidVersion !== desktopVersion) throw new Error('Android 与 macOS 必须使用相同的简洁正式版本号。');

const assets = [
  [resolve(root, 'app/build/outputs/apk/release/南枫AI.apk'), `Nanfeng-AI-Android-${androidVersion}.apk`],
  [resolve(root, `desktop/src-tauri/target/release/bundle/dmg/Nanfeng-AI-macOS-${desktopVersion}.dmg`), `Nanfeng-AI-macOS-${desktopVersion}.dmg`],
];
await mkdir(destination, { recursive: true });
for (const [source, name] of assets) await copyFile(source, resolve(destination, name));
console.log(JSON.stringify({ version: androidVersion, destination, assets: assets.map(([, name]) => name) }));
