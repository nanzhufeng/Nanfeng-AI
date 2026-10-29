import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile,access} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
async function fixture(){
 const root=resolve(desktop,'../.runtime-qa/development-entry-tests',randomUUID());
 const app=join(root,'desktop'),scripts=join(app,'scripts'),launcher=join(app,'src-tauri/resources/alignment-launcher');
 await mkdir(scripts,{recursive:true});await mkdir(launcher,{recursive:true});
 await copyFile(join(desktop,'scripts/start-windows-current-alignment.ps1'),join(scripts,'start-windows-current-alignment.ps1'));
 await copyFile(join(desktop,'src-tauri/resources/alignment-launcher/Start.ps1'),join(launcher,'Start.ps1'));
 const local=join(root,'local-appdata');await mkdir(local);
 const stub=String.raw`param($ExecutablePath,$RootParent,$StartupMode,$Root,[switch]$Visible,$DebugPort,$OutputRoot)
$ErrorActionPreference='Stop'
if(!$Root){$Root=Join-Path $PSScriptRoot '../owned-test-root'}
New-Item -ItemType Directory -Path $Root -Force | Out-Null
$receiptPath=Join-Path $Root ([guid]::NewGuid().ToString()+'.json')
$data=@{launchReceipt=$receiptPath;pid=1;root=[IO.Path]::GetFullPath($Root);sha256=(Get-FileHash -LiteralPath $ExecutablePath).Hash;visible=[bool]$Visible;executable=$ExecutablePath;startupMode=$StartupMode}
$text=$data|ConvertTo-Json -Compress -EscapeHandling EscapeNonAscii
[IO.File]::WriteAllText($receiptPath,$text,[Text.UTF8Encoding]::new($false))
$text
`;
 await writeFile(join(scripts,'start-windows-acceptance.ps1'),stub);
 const state=join(local,'NanfengAI-Windows-Alignment-Acceptance/launch-root.json');await mkdir(dirname(state),{recursive:true});await writeFile(state,'UNRELATED_ALIGNMENT_STATE');
 const candidateRoot=join(app,'output/windows-acceptance-release');await mkdir(candidateRoot,{recursive:true});
 async function candidate(text){const bytes=Buffer.from(text);const sha=createHash('sha256').update(bytes).digest('hex').toUpperCase();const dir=join(candidateRoot,sha);await mkdir(dir);const executable=join(dir,'NanfengAI-Windows-Acceptance.exe');await writeFile(executable,bytes);const receipt={identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256:sha,executable,buildProfile:'release'};await writeFile(join(candidateRoot,'current.json'),JSON.stringify(receipt));return receipt;}
 const entry=join(scripts,'start-windows-current-alignment.ps1');
 const run=()=>spawnSync('pwsh',['-NoProfile','-File',entry,'-DevelopmentCandidate','-Hidden','-PassThru'],{encoding:'utf8',windowsHide:true,timeout:30000,env:{...process.env,LOCALAPPDATA:local}});
 return {root,app,local,state,candidateRoot,candidate,run};
}
test('development entry follows the current release candidate and retains its own root without reading alignment state',async()=>{
 const f=await fixture();const first=await f.candidate('synthetic development executable one');
 const run1=f.run();assert.equal(run1.status,0,run1.stdout+run1.stderr);const a=JSON.parse(run1.stdout.trim());
 assert.equal(a.sha256,first.sha256);assert.equal(a.visible,false);
 const second=await f.candidate('synthetic development executable two');const run2=f.run();assert.equal(run2.status,0,run2.stdout+run2.stderr);const b=JSON.parse(run2.stdout.trim());
 assert.equal(b.sha256,second.sha256);assert.equal(a.root,b.root);
 assert.equal(await readFile(f.state,'utf8'),'UNRELATED_ALIGNMENT_STATE');
 const saved=JSON.parse(await readFile(join(f.local,'NanfengAI-Windows-Development-Acceptance/launch-root.json'),'utf8'));
 assert.deepEqual(Object.keys(saved).sort(),['identifier','root']);assert.equal(saved.root,a.root);
});
test('development entry rejects an executable hash mismatch before the startup adapter runs',async()=>{
 const f=await fixture();const receipt=await f.candidate('synthetic expected executable');await writeFile(receipt.executable,'different bytes');
 const result=f.run();assert.notEqual(result.status,0);assert.match(result.stdout+result.stderr,/开发候选.*收据|校验/);
 assert.equal(await access(join(f.local,'NanfengAI-Windows-Development-Acceptance/launch-root.json')).then(()=>true,()=>false),false);
 assert.equal(await readFile(f.state,'utf8'),'UNRELATED_ALIGNMENT_STATE');
});
