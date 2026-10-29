import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {alignmentProfile} from '../scripts/windows-alignment-profile.mjs';

const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const hash=b=>createHash('sha256').update(b).digest('hex').toUpperCase();
async function fixture(){
 const repo=resolve(desktop,'../.runtime-qa/alignment-profile-tests',randomUUID());const app=join(repo,'desktop');const scripts=join(app,'scripts');await mkdir(scripts,{recursive:true});
 await copyFile(join(desktop,'scripts/prepare-windows-alignment-launcher.mjs'),join(scripts,'prepare-windows-alignment-launcher.mjs'));
 try{await copyFile(join(desktop,'scripts/windows-alignment-profile.mjs'),join(scripts,'windows-alignment-profile.mjs'));}catch(error){if(error.code!=='ENOENT')throw error;}
 for(const n of ['start-windows-acceptance.ps1','verify-windows-media-tools.ps1'])await writeFile(join(scripts,n),'# synthetic companion, never run\n');
 const target=join(repo,'shared target');const receiptHashes={};
 for(const profile of ['debug','release']){
  const bytes=Buffer.from(`synthetic ${profile} executable, never run`);const sha256=hash(bytes);receiptHashes[profile]=sha256;
  const cache=join(target,`x86_64-pc-windows-msvc/${profile}/nanfeng-ai-desktop-spike.exe`);await mkdir(dirname(cache),{recursive:true});await writeFile(cache,bytes);
  const output=join(app,'output',profile==='release'?'windows-acceptance-release':'windows-acceptance');await mkdir(output,{recursive:true});
  await writeFile(join(output,'current.json'),JSON.stringify({identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256,buildProfile:profile}));
 }
 const evidence=join(repo,'docs/review/synthetic/native.json');await mkdir(dirname(evidence),{recursive:true});
 await writeFile(evidence,JSON.stringify({pass:true,candidateSha256:receiptHashes.release,runtimeInfo:{platform:'windows',buildProfile:'release',sourceInputSha256:'1'.repeat(64),sourceInputFileCount:401,sourceInputRecipe:'nfai.desktop.source-input.v1',version:'0.6.0-p6d-dev',gitCommit:'2'.repeat(40),gitWorktreeDirty:true}}));
 const manifest=join(app,'src-tauri/resources/alignment-launcher/executable.json');await mkdir(dirname(manifest),{recursive:true});await writeFile(manifest,'existing manifest sentinel');
 return {repo,app,scripts,target,evidence,manifest,receiptHashes};
}
function prepare(f){return spawnSync(process.execPath,['--max-old-space-size=2048',join(f.scripts,'prepare-windows-alignment-launcher.mjs'),f.evidence,'--profile=release'],{cwd:f.repo,windowsHide:true,encoding:'utf8',timeout:15000,env:{...process.env,CARGO_TARGET_DIR:f.target,NODE_OPTIONS:'--max-old-space-size=2048'}});}
test('verified Release runtime prepares the Release manifest from its own receipt and cache',async()=>{
 const f=await fixture();const result=prepare(f);assert.equal(result.status,0,result.stderr);
 const manifest=JSON.parse(await readFile(f.manifest,'utf8'));assert.equal(manifest.buildProfile,'release');assert.equal(manifest.sha256,f.receiptHashes.release);assert.equal(manifest.sourceInputSha256,'1'.repeat(64));
 assert.equal(await readFile(join(dirname(f.manifest),'scripts/start-windows-acceptance.ps1'),'utf8'),'# synthetic companion, never run\n');
});

test('mismatched runtime profile refuses preparation before changing the existing manifest',async()=>{
 const f=await fixture();const data=JSON.parse(await readFile(f.evidence,'utf8'));data.runtimeInfo.buildProfile='debug';await writeFile(f.evidence,JSON.stringify(data));
 const result=prepare(f);assert.notEqual(result.status,0);assert.equal(await readFile(f.manifest,'utf8'),'existing manifest sentinel');
});

test('changed Release cache cannot prepare a manifest for the previously verified candidate',async()=>{
 const f=await fixture();await writeFile(join(f.target,'x86_64-pc-windows-msvc/release/nanfeng-ai-desktop-spike.exe'),'different synthetic bytes');
 const result=prepare(f);assert.notEqual(result.status,0);assert.equal(await readFile(f.manifest,'utf8'),'existing manifest sentinel');
});

test('profile selection preserves default Debug and rejects unknown or conflicting flags',()=>{
 assert.equal(alignmentProfile([]),'debug');assert.equal(alignmentProfile(['--profile=debug']),'debug');assert.equal(alignmentProfile(['--profile=release']),'release');
 for(const args of [['--profile=other'],['--profile=debug','--profile=release'],['--profile=release','--profile=release']])assert.throws(()=>alignmentProfile(args));
});
