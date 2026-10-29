import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const [lock, windowsConfig, build, prepare, verifier, launcher, owner, transcription, lib] = await Promise.all([
  readFile(resolve(root, 'scripts/windows-media-tools.lock.json'), 'utf8').then(JSON.parse),
  readFile(resolve(root, 'src-tauri/tauri.windows.conf.json'), 'utf8').then(JSON.parse),
  readFile(resolve(root, 'scripts/build-windows.mjs'), 'utf8'),
  readFile(resolve(root, 'scripts/prepare-windows-media-tools.mjs'), 'utf8'),
  readFile(resolve(root, 'scripts/verify-windows-media-tools.ps1'), 'utf8'),
  readFile(resolve(root, 'scripts/start-windows-acceptance.ps1'), 'utf8'),
  readFile(resolve(root, 'src-tauri/src/desktop_media_tools_v1.rs'), 'utf8'),
  readFile(resolve(root, 'src-tauri/src/desktop_transcription_v1.rs'), 'utf8'),
  readFile(resolve(root, 'src-tauri/src/lib.rs'), 'utf8'),
]);

test('Windows bundle pins one x64 LGPL FFmpeg asset and verifies every runtime file', () => {
  assert.equal(lock.schemaVersion, 1);
  assert.equal(lock.target, 'x86_64-pc-windows-msvc');
  assert.equal(lock.license, 'LGPL-3.0-or-later');
  assert.match(lock.archiveUrl, /releases\/download\/autobuild-2026-09-17-13-19\//);
  assert.match(lock.archiveSha256, /^[a-f0-9]{64}$/);
  assert.deepEqual(lock.runtimeFiles.map(file => file.name).sort(), [
    'avcodec-63.dll', 'avdevice-63.dll', 'avfilter-12.dll', 'avformat-63.dll',
    'avutil-61.dll', 'ffmpeg.exe', 'ffprobe.exe', 'swresample-7.dll', 'swscale-10.dll'
  ]);
  assert.ok(lock.runtimeFiles.every(file => file.bytes > 0 && /^[a-f0-9]{64}$/i.test(file.sha256)));
  for (const file of lock.runtimeFiles) {
    assert.match(owner, new RegExp(file.sha256, 'i'));
    assert.match(verifier, new RegExp(file.sha256, 'i'));
  }
  assert.match(owner, new RegExp(lock.licenseFile.sha256, 'i'));
  assert.match(verifier, new RegExp(lock.licenseFile.sha256, 'i'));
  assert.match(owner, /4d98c6ce49419a9c249a97afeda21d7d217a8d15867443eff9a45113f9ac1541/);
  assert.match(verifier, /4d98c6ce49419a9c249a97afeda21d7d217a8d15867443eff9a45113f9ac1541/);
  assert.doesNotMatch(JSON.stringify(lock.runtimeFiles), /ffplay/i);
  assert.match(prepare, /curl\.exe/);
  assert.match(prepare, /await sha256\(pending\) !== lock\.archiveSha256/);
  assert.match(prepare, /verifyPrepared\(\)/);
});

test('Windows build bundles and stages the prepared media directory before issuing a receipt', () => {
  assert.equal(
    windowsConfig.bundle.resources['resources/media-tools/windows-x86_64'],
    'media-tools/windows-x86_64'
  );
  const prepareIndex = build.indexOf("scripts/prepare-windows-media-tools.mjs");
  const tauriIndex = build.indexOf("node_modules/@tauri-apps/cli/tauri.js");
  assert.ok(prepareIndex >= 0 && prepareIndex < tauriIndex);
  assert.match(build, /mediaTools = resolve\(directory, 'media-tools\/windows-x86_64'\)/);
  assert.match(build, /identifier: 'com\.nanzhufeng\.ai\.desktop', sha256, executable, mediaTools/);
  assert.match(build, /identifier: 'com\.nanzhufeng\.ai\.desktop\.windowsacceptance', sha256, executable, mediaTools/);
  assert.match(build, /let configuredTarget = process\.env\.CARGO_TARGET_DIR/);
  assert.match(build, /configuredTarget \|\| 'src-tauri\/target'/);
  assert.match(build, /process\.env\.NANFENG_AI_WINDOWS_OUTPUT_DIR \|\| 'output'/);
  assert.match(build, /const source = windowsBuiltExecutable/g);
  assert.doesNotMatch(build, /const source = resolve\(root, 'src-tauri\/target\/x86_64-pc-windows-msvc/);
  assert.match(build, /resolve\(windowsOutputRoot, buildProfile === 'release' \? 'windows-acceptance-release' : 'windows-acceptance'\)/);
  assert.match(launcher, /\[string\]\$OutputRoot = ''/);
  assert.match(launcher, /\[string\]\$RootParent = ''/);
  assert.match(launcher, /Join-Path \$buildOutputRoot 'windows-acceptance\/current\.json'/);
  assert.match(launcher, /TEMP = \$temporaryParent/);
  assert.match(launcher, /TMP = \$temporaryParent/);
  assert.match(launcher, /验收根父目录必须是受控普通目录/);
  assert.match(launcher, /Windows 独立验收构建与收据不一致/);
});

test('transcription resolves Tauri resources, never PATH, and reaps its exact hidden child on cancel', () => {
  assert.match(lib, /app\.path\(\)\.resource_dir\(\)\.ok\(\)/);
  assert.match(lib, /media_resource_dir\.as_deref\(\)/);
  assert.match(owner, /resource_dir\.join\("media-tools\/windows-x86_64"\)/);
  assert.match(owner, /validate_x64_pe/);
  assert.match(owner, /sha256_file/);
  assert.match(owner, /creation_flags\(0x0800_0000\)/);
  assert.match(owner, /terminate_exact_child\(&mut child\)/);
  assert.match(owner, /let _ = child\.kill\(\);\s*let _ = child\.wait\(\);/s);
  assert.match(lib, /desktop_media_tools_v1::probe_duration_millis/);
  assert.doesNotMatch(transcription, /fn find_binary\(/);
  assert.doesNotMatch(transcription, /Command::new\("cmd(?:\.exe)?"\)/i);
});
