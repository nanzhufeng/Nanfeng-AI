import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile,utimes,access} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';

const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const repository=resolve(desktop,'..');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex').toUpperCase();

async function fixture(){
 const root=join(repository,'.runtime-qa','windows-build-receipt-tests',randomUUID());
 const app=join(root,'desktop');const scripts=join(app,'scripts');
 const output=join(root,'output');const target=join(root,'fixed target');
 await mkdir(scripts,{recursive:true});
 for(const name of ['build-windows.mjs','prune-windows-acceptance-output.mjs','windows-public-cloud-config.mjs'])await copyFile(join(desktop,'scripts',name),join(scripts,name));
 await writeFile(join(scripts,'build.mjs'),'// synthetic external frontend compiler boundary\n');
 await writeFile(join(scripts,'prepare-windows-media-tools.mjs'),'// synthetic external resource preparation boundary\n');
 const cli=join(app,'node_modules/@tauri-apps/cli');await mkdir(cli,{recursive:true});
 await writeFile(join(cli,'tauri.js'),"require('node:fs').writeFileSync(process.env.FIXTURE_TAURI_ARGS_FILE,JSON.stringify(process.argv.slice(2)));process.exit(process.env.FIXTURE_COMPILER_FAIL==='1'?23:0);\n");
 const media=join(app,'src-tauri/resources/media-tools/windows-x86_64');await mkdir(media,{recursive:true});
 await writeFile(join(media,'fixture-tool.bin'),'synthetic resource');
 const binaries={};
 for(const profile of ['debug','release']){
  const path=join(target,'x86_64-pc-windows-msvc',profile,'nanfeng-ai-desktop-spike.exe');
  await mkdir(dirname(path),{recursive:true});const bytes=Buffer.from(`synthetic ${profile} binary, never executable`);
  await writeFile(path,bytes);binaries[profile]={path,bytes,sha256:hash(bytes)};
 }
 const oldHash='A'.repeat(64);const oldDir=join(output,'windows-user',oldHash);
 await mkdir(oldDir,{recursive:true});await writeFile(join(oldDir,'NanfengAI.exe'),'old owned synthetic candidate');
 await utimes(oldDir,new Date('2000-01-01'),new Date('2000-01-01'));
 const previous={identifier:'com.nanzhufeng.ai.desktop',sha256:oldHash,executable:join(oldDir,'NanfengAI.exe')};
 const current=join(output,'windows-user/current.json');await writeFile(current,JSON.stringify(previous));
 const preload=join(root,'hidden-synthetic-boundaries.mjs');
 await writeFile(preload,`import {createRequire,syncBuiltinESMExports} from 'node:module';
const require=createRequire(import.meta.url);const cp=require('node:child_process');const original=cp.spawnSync;
cp.spawnSync=(command,args,options)=>{if(command==='pwsh'&&args.some(x=>x.includes('Get-Process')))return {status:0,stdout:'',stderr:''};return original(command,args,{...options,windowsHide:true});};
syncBuiltinESMExports();\n`);
 return {root,app,scripts,output,target,binaries,oldDir,current,previous,preload};
}
function build(f,options,extraEnv={},defaultBundle=false){
 return spawnSync(process.execPath,['--max-old-space-size=2048','--import',pathToFileURL(f.preload).href,join(f.scripts,'build-windows.mjs'),...options,...(defaultBundle?[]:['--no-bundle','--target=x86_64-pc-windows-msvc']),'--preserve-output'],{
  windowsHide:true,encoding:'utf8',timeout:30000,cwd:f.app,
  env:{...process.env,USERPROFILE:join(f.root,'synthetic-home'),NODE_OPTIONS:'--max-old-space-size=2048',CARGO_TARGET_DIR:f.target,NANFENG_AI_WINDOWS_OUTPUT_DIR:f.output,FIXTURE_TAURI_ARGS_FILE:join(f.root,'compiler-args.json'),
   NANFENG_DESKTOP_BUNDLED_SUPABASE_URL:'https://synthetic-fixture.supabase.co',NANFENG_DESKTOP_BUNDLED_SUPABASE_PUBLISHABLE_KEY:'sb_publishable_synthetic_fixture_only_1234',...extraEnv},
 });
}

test('daily Debug preserve-output retains an older successful candidate while promoting the newly compiled bytes',async()=>{
 const f=await fixture();const result=build(f,['--debug']);assert.equal(result.status,0,result.stderr);
 const receipt=JSON.parse(await readFile(f.current,'utf8'));
 assert.equal(receipt.sha256,f.binaries.debug.sha256);
 assert.deepEqual(await readFile(receipt.executable),f.binaries.debug.bytes);
 assert.equal(await readFile(join(f.oldDir,'NanfengAI.exe'),'utf8'),'old owned synthetic candidate','explicit preserve-output must protect old output');
});

test('default Windows bundle flow pins the x64 target and promotes the Release daily receipt',async()=>{
 const f=await fixture();const result=build(f,[],{},true);assert.equal(result.status,0,result.stderr);
 const receipt=JSON.parse(await readFile(f.current,'utf8'));
 assert.equal(receipt.sha256,f.binaries.release.sha256);
 const args=JSON.parse(await readFile(join(f.root,'compiler-args.json'),'utf8'));
 assert.deepEqual(args.slice(args.indexOf('--target'),args.indexOf('--target')+2),['--target','x86_64-pc-windows-msvc']);
 assert.ok(!args.includes('--debug'));assert.ok(!args.includes('--no-bundle'));
 assert.equal(receipt.buildProfile,'release');
});

test('compiler failure leaves the daily receipt and previous executable unchanged',async()=>{
 const f=await fixture();const before=await readFile(f.current);const result=build(f,[],{FIXTURE_COMPILER_FAIL:'1'});
 assert.equal(result.status,23);assert.deepEqual(await readFile(f.current),before);
 assert.equal(await readFile(join(f.oldDir,'NanfengAI.exe'),'utf8'),'old owned synthetic candidate');
 await assert.rejects(access(join(f.output,'windows-user',f.binaries.release.sha256)));
});

test('acceptance Debug publishes only its own receipt and never changes the daily entry',async()=>{
 const f=await fixture();const before=await readFile(f.current);const result=build(f,['--acceptance','--debug']);
 assert.equal(result.status,0,result.stderr);assert.deepEqual(await readFile(f.current),before);
 const receipt=JSON.parse(await readFile(join(f.output,'windows-acceptance/current.json'),'utf8'));
 assert.equal(receipt.identifier,'com.nanzhufeng.ai.desktop.windowsacceptance');assert.equal(receipt.sha256,f.binaries.debug.sha256);
 assert.deepEqual(await readFile(receipt.executable),f.binaries.debug.bytes);
});

test('a mismatched existing immutable candidate is refused without replacing it or promoting the receipt',async()=>{
 const f=await fixture();const before=await readFile(f.current);const dir=join(f.output,'windows-user',f.binaries.release.sha256);
 await mkdir(dir,{recursive:true});const exe=join(dir,'NanfengAI.exe');await writeFile(exe,'synthetic mismatched bytes');
 const result=build(f,[]);assert.notEqual(result.status,0);assert.match(result.stderr,/staged executable hash mismatch/);
 assert.deepEqual(await readFile(f.current),before);assert.equal(await readFile(exe,'utf8'),'synthetic mismatched bytes');
});

test('daily Release promotes the Release binary and its profile instead of leaving the old daily receipt',async()=>{
 const f=await fixture();const result=build(f,[]);assert.equal(result.status,0,result.stderr);
 const receipt=JSON.parse(await readFile(f.current,'utf8'));
 assert.equal(receipt.sha256,f.binaries.release.sha256,'daily launcher must resolve this Release build, not the previous binary');
 assert.deepEqual(await readFile(receipt.executable),f.binaries.release.bytes);
 assert.equal(receipt.identifier,'com.nanzhufeng.ai.desktop');
 assert.equal(receipt.buildProfile,'release');assert.equal(receipt.build,'windows-x64-release');
 assert.equal(await readFile(join(receipt.mediaTools,'fixture-tool.bin'),'utf8'),'synthetic resource');
 assert.equal(await readFile(join(f.oldDir,'NanfengAI.exe'),'utf8'),'old owned synthetic candidate');
});
