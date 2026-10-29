import { readFile, readdir, lstat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, join } from 'node:path';

// Explicit packaging inputs, separate from the executable's frozen source
// identity. No user stores, environment enumeration or complete build copies.
export async function collectAlignmentPackageInputs(desktop, target, generated, patchedUtils, { productionCandidate = false } = {}) {
  const records = [];
  const sources = new Map();
  let total = 0;
  async function digest(path) {
    const hash = createHash('sha256');
    for await (const chunk of createReadStream(path)) hash.update(chunk);
    return hash.digest('hex');
  }
  async function add(path, id) {
    path = resolve(path);
    const meta = await lstat(path);
    if (!meta.isFile() || meta.isSymbolicLink()) throw new Error('包装输入不是独立普通文件：' + id);
    if (/(?:^|[\\/])\.env(?:\.|$)|\.(?:pem|key|pfx|p12|keystore|jks|p8)$/i.test(path) || /(?:credentials|secrets)\.json$/i.test(path)) throw new Error('包装资源范围出现凭据文件名，未读取内容。');
    if (!sources.has(path)) {
      total += meta.size;
      if (sources.size >= 2000 || total > 1024 ** 3) throw new Error('包装输入超出本轮已核对范围。');
      sources.set(path, { bytes: meta.size, sha256: await digest(path) });
    }
    records.push({ id, ...sources.get(path) });
  }
  async function tree(directory, prefix) {
    const entries = await readdir(directory, { withFileTypes: true });
    for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, 'en'))) {
      if (entry.isSymbolicLink()) throw new Error('包装输入含重定向路径。');
      if (entry.isDirectory()) await tree(join(directory, entry.name), prefix + entry.name + '/');
      else await add(join(directory, entry.name), prefix + entry.name);
    }
  }
  const builders = productionCandidate
    ? ['repack-windows-production-candidate.mjs', 'alignment-installer-policy.mjs', 'alignment-package-inputs.mjs']
    : ['repack-windows-alignment-installer.mjs', 'alignment-installer-policy.mjs', 'alignment-package-inputs.mjs', 'windows-alignment-profile.mjs'];
  for (const name of builders) await add(join(desktop, 'scripts', name), 'builder/' + name);
  if (productionCandidate) {
    for (const name of ['tauri.conf.json', 'tauri.windows.conf.json']) await add(join(desktop, 'src-tauri', name), 'config/' + name);
    await add(join(desktop, 'src-tauri/resources/alignment-launcher/Check-InstalledProcess.ps1'), 'guard/Check-InstalledProcess.ps1');
  } else {
    await add(join(desktop, 'src-tauri/tauri.windows-alignment-installer.conf.json'), 'config/alignment-installer.json');
  }
  for (const name of ['installer.nsi', 'utils.nsh', 'FileAssociation.nsh', 'SimpChinese.nsh']) await add(join(target, 'nsis/x64', name), 'fresh-nsis/' + name);
  if (sources.get(resolve(target, 'nsis/x64/installer.nsi')).sha256 !== createHash('sha256').update(generated).digest('hex')) throw new Error('新NSIS模板在收集输入期间变化。');
  records.push({ id: 'patched-nsis/utils.nsh', bytes: Buffer.byteLength(patchedUtils), sha256: createHash('sha256').update(patchedUtils).digest('hex') });
  const defines = new Map([...generated.matchAll(/^!define\s+([A-Z0-9_]+)\s+"([^"]*)"/gm)].map(match => [match[1], match[2]]));
  const expand = value => {
    for (let count = 0; count < 8 && /\$\{/.test(value); count++) value = value.replace(/\$\{([A-Z0-9_]+)\}/g, (all, name) => defines.get(name) ?? all);
    if (/\$\{/.test(value)) throw new Error('新NSIS文件输入变量尚未核对。');
    return value;
  };
  let index = 0;
  for (const line of generated.split(/\r?\n/).filter(line => /^\s*File\s/.test(line))) {
    const source = line.match(/"([^"]*)"\s*$/)?.[1];
    if (source === undefined) throw new Error('新NSIS File语法已变化。');
    const path = expand(source);
    if (path) await add(path, 'embedded-file/' + index);
    index++;
  }
  const plugins = defines.get('ADDITIONALPLUGINSPATH');
  if (plugins) await tree(expand(plugins), 'tauri-plugins/');
  for (const name of ['INSTALLERICON', 'UNINSTALLERICON']) {
    if (defines.get(name)) await add(expand(defines.get(name)), 'icon/' + name);
  }
  if (!productionCandidate) await tree(join(desktop, 'src-tauri/resources/alignment-launcher'), 'launcher/');
  await tree(join(desktop, 'src-tauri/resources/media-tools/windows-x86_64'), 'media/');
  await tree(resolve(process.env.LOCALAPPDATA, 'tauri/NSIS'), 'tool/nsis/');
  records.sort((a, b) => a.id.localeCompare(b.id, 'en'));
  const manifest = { format: productionCandidate ? 'nfai.windows-production-candidate.inputs.v1' : 'nfai.windows-alignment-package.inputs.v1', files: records };
  const bytes = JSON.stringify(manifest);
  return {
    manifest, sha256: createHash('sha256').update(bytes).digest('hex'),
    async verify() {
      for (const [path, expected] of sources) {
        if ((await lstat(path)).size !== expected.bytes || await digest(path) !== expected.sha256) throw new Error('包装期间输入变化，旧产物已保留。');
      }
    },
  };
}
