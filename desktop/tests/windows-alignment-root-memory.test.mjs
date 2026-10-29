import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
async function fixture(){
 const root=resolve(desktop,'../.runtime-qa/alignment-root-memory-tests',randomUUID());const scripts=join(root,'desktop/scripts');await mkdir(scripts,{recursive:true});
 await copyFile(join(desktop,'scripts/start-windows-current-alignment.ps1'),join(scripts,'start-windows-current-alignment.ps1'));
 const dir=join(root,'desktop/output/windows-alignment-acceptance/candidate');const launcher=join(dir,'alignment-launcher');await mkdir(join(launcher,'scripts'),{recursive:true});
 await copyFile(join(desktop,'src-tauri/resources/alignment-launcher/Start.ps1'),join(launcher,'Start.ps1'));
 const helper=`param([string]$ExecutablePath,[string]$RootParent,[string]$StartupMode,[string]$Root,[int]$DebugPort,[switch]$Visible,[switch]$RunInPlace,[string]$OutputRoot)
if(!$Root){$Root=Join-Path $env:LOCALAPPDATA ('nanfeng-ai-windows-acceptance.'+[guid]::NewGuid().ToString())}
New-Item -ItemType Directory -Path $Root -Force | Out-Null
$sha=(Get-FileHash -LiteralPath $ExecutablePath -Algorithm SHA256).Hash
@{executable=$ExecutablePath;visible=[bool]$Visible;root=$Root;sha256=$sha;launchReceipt=(Join-Path $Root 'synthetic-launch.json');runInPlace=[bool]$RunInPlace;outputRoot=$OutputRoot}|ConvertTo-Json -Compress -EscapeHandling EscapeNonAscii\n`;
 await writeFile(join(scripts,'start-windows-acceptance.ps1'),helper);await writeFile(join(launcher,'scripts/start-windows-acceptance.ps1'),helper);
 const exe=join(dir,'nanfeng-ai-desktop-spike.exe');const bytes=Buffer.from('synthetic acceptance image, never executable');await writeFile(exe,bytes);const sha256=createHash('sha256').update(bytes).digest('hex').toUpperCase();
 const manifest={identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256,sourceInputSha256:'1'.repeat(64),buildProfile:'release',entryRootPolicy:'shared-alignment-root-v1'};
 await writeFile(join(launcher,'executable.json'),JSON.stringify(manifest));
 const receipt={installerIdentifier:'com.nanzhufeng.ai.desktop.windowsalignmentinstaller',applicationIdentifier:manifest.identifier,sha256,executable:exe,applicationSourceInputSha256:manifest.sourceInputSha256,buildProfile:'release'};
 await writeFile(join(dirname(dir),'current.json'),JSON.stringify(receipt));
 const local=join(root,'isolated-local-appdata');await mkdir(local);return {root,scripts,local,state:join(local,'NanfengAI-Windows-Alignment-Acceptance/launch-root.json')};
}
function launch(f,explicitRoot){return spawnSync('pwsh',['-NoProfile','-File',join(f.scripts,'start-windows-current-alignment.ps1'),'-Hidden','-PassThru',...(explicitRoot?['-Root',explicitRoot]:[])],{windowsHide:true,encoding:'utf8',timeout:15000,env:{...process.env,LOCALAPPDATA:f.local}});}
test('two ordinary repository launches return to one remembered root without copying business text',async()=>{
 const f=await fixture();const first=launch(f);assert.equal(first.status,0,first.stdout+first.stderr);const one=JSON.parse(first.stdout);
 const second=launch(f);assert.equal(second.status,0,second.stdout+second.stderr);const two=JSON.parse(second.stdout);
 assert.equal(two.root,one.root,'double-clicking the same entry must retain its data root');assert.equal(two.visible,false);assert.equal(two.runInPlace,false);
 assert.equal(one.outputRoot,join(f.root,'desktop/output'),'repository runner must use its fixed output, not the deeply nested package helper directory');
 const saved=JSON.parse(await readFile(f.state,'utf8'));assert.deepEqual(Object.keys(saved).sort(),['identifier','root']);assert.equal(saved.root,one.root);
});

test('an empty saved root fails explicitly instead of starting an unremembered replacement',async()=>{
 const f=await fixture();await mkdir(dirname(f.state),{recursive:true});const saved=JSON.stringify({identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',root:''});await writeFile(f.state,saved);
 const result=launch(f);assert.equal(result.status,1);assert.match(result.stdout,/入口记录损坏/);assert.equal(await readFile(f.state,'utf8'),saved);
});

test('an explicit root bypasses an unreadable saved state and never changes it',async()=>{
 const f=await fixture();await mkdir(dirname(f.state),{recursive:true});await writeFile(f.state,'poisoned saved-state sentinel');const root=join(f.local,'explicit-owned-root');
 const result=launch(f,root);assert.equal(result.status,0,result.stdout+result.stderr);assert.equal(JSON.parse(result.stdout).root,root);assert.equal(await readFile(f.state,'utf8'),'poisoned saved-state sentinel');
});

test('a foreign remembered identity is rejected without replacing its record',async()=>{
 const f=await fixture();await mkdir(dirname(f.state),{recursive:true});const saved=JSON.stringify({identifier:'foreign-fixture',root:join(f.local,'do-not-open')});await writeFile(f.state,saved);
 const result=launch(f);assert.equal(result.status,1);assert.match(result.stdout,/身份不符/);assert.equal(await readFile(f.state,'utf8'),saved);
});
