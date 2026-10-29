import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile,access} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
async function fixture(){
 const root=resolve(desktop,'../.runtime-qa/alignment-entry-profile-tests',randomUUID());const scripts=join(root,'desktop/scripts');await mkdir(scripts,{recursive:true});
 await copyFile(join(desktop,'scripts/start-windows-current-alignment.ps1'),join(scripts,'start-windows-current-alignment.ps1'));
 await writeFile(join(scripts,'start-windows-acceptance.ps1'),`param([string]$ExecutablePath,[string]$RootParent,[string]$StartupMode,[string]$Root,[int]$DebugPort,[switch]$Visible)
New-Item -ItemType Directory -Path $Root -Force | Out-Null
Set-Content -LiteralPath (Join-Path $Root 'launch-called') -Value 'synthetic companion only'
@{executable=$ExecutablePath;visible=[bool]$Visible;root=$Root}|ConvertTo-Json -Compress\n`);
 const output=join(root,'desktop/output/windows-alignment-acceptance');const entries={};
 for(const profile of ['debug','release']){
  const dir=join(output,profile);await mkdir(join(dir,'alignment-launcher'),{recursive:true});const exe=join(dir,'nanfeng-ai-desktop-spike.exe');
  const bytes=Buffer.from(`synthetic ${profile}, never executable`);await writeFile(exe,bytes);const sha256=createHash('sha256').update(bytes).digest('hex').toUpperCase();
  const manifest={identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256,sourceInputSha256:'1'.repeat(64),buildProfile:profile};
  const manifestPath=join(dir,'alignment-launcher/executable.json');await writeFile(manifestPath,JSON.stringify(manifest));
  const receipt={installerIdentifier:'com.nanzhufeng.ai.desktop.windowsalignmentinstaller',applicationIdentifier:manifest.identifier,sha256,executable:exe,applicationSourceInputSha256:manifest.sourceInputSha256,buildProfile:profile};
  const pointer=join(output,profile==='release'?'current-release.json':'current.json');await writeFile(pointer,JSON.stringify(receipt));entries[profile]={exe,pointer,manifestPath,manifest,receipt};
 }
 return {root,scripts,entries,data:join(root,'owned-data')};
}
function launch(f,profile){return spawnSync('pwsh',['-NoProfile','-File',join(f.scripts,'start-windows-current-alignment.ps1'),'-Hidden','-Root',f.data,...(profile?['-PackageProfile',profile]:[])],{windowsHide:true,encoding:'utf8',timeout:15000});}
test('explicit Release entry resolves Release while default still resolves the current Debug package',async()=>{
 const f=await fixture();const before=await readFile(f.entries.debug.pointer);let run=launch(f,'release');assert.equal(run.status,0,run.stdout+run.stderr);
 let result=JSON.parse(run.stdout);assert.equal(result.executable,f.entries.release.exe);assert.equal(result.visible,false);
 run=launch(f);assert.equal(run.status,0,run.stdout+run.stderr);result=JSON.parse(run.stdout);assert.equal(result.executable,f.entries.debug.exe);
 assert.deepEqual(await readFile(f.entries.debug.pointer),before);
});
test('manifest profile mismatch stops before invoking the companion',async()=>{
 const f=await fixture();await writeFile(f.entries.release.manifestPath,JSON.stringify({...f.entries.release.manifest,buildProfile:'debug'}));
 const run=launch(f,'release');assert.equal(run.status,1);assert.match(run.stdout,/配套启动入口与程序不一致/);await assert.rejects(access(join(f.data,'launch-called')));
});
test('Release entry rejects a Debug-tagged receipt without falling back to the default',async()=>{
 const f=await fixture();await writeFile(f.entries.release.pointer,JSON.stringify({...f.entries.release.receipt,buildProfile:'debug'}));
 const run=launch(f,'release');assert.equal(run.status,1);assert.match(run.stdout,/构建类型不匹配/);await assert.rejects(access(join(f.data,'launch-called')));
});
