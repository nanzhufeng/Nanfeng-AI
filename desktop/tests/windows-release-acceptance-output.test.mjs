import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,copyFile,utimes,access} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {pruneWindowsReleaseAcceptanceOutput} from '../scripts/prune-windows-acceptance-output.mjs';

test('Release acceptance uses its own immutable receipt and leaves Debug and daily entries intact',async()=>{
 const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..');
 const root=join(desktop,'../.runtime-qa/release-receipt-tests',randomUUID());
 const app=join(root,'desktop');const scripts=join(app,'scripts');await mkdir(scripts,{recursive:true});
 for(const name of ['build-windows.mjs','prune-windows-acceptance-output.mjs','windows-public-cloud-config.mjs'])await copyFile(join(desktop,'scripts',name),join(scripts,name));
 for(const name of ['build.mjs','prepare-windows-media-tools.mjs'])await writeFile(join(scripts,name),'// owned synthetic compiler boundary\n');
 const cli=join(app,'node_modules/@tauri-apps/cli');await mkdir(cli,{recursive:true});await writeFile(join(cli,'tauri.js'),'process.exit(0);\n');
 const target=join(root,'fixed target');const binary=join(target,'x86_64-pc-windows-msvc/release/nanfeng-ai-desktop-spike.exe');await mkdir(dirname(binary),{recursive:true});
 const bytes=Buffer.from('synthetic release acceptance bytes - never executable');await writeFile(binary,bytes);
 const media=join(app,'src-tauri/resources/media-tools/windows-x86_64');await mkdir(media,{recursive:true});await writeFile(join(media,'fixture.bin'),'fixture');
 const output=join(root,'output');
 for(const name of ['windows-user','windows-acceptance']){await mkdir(join(output,name),{recursive:true});await writeFile(join(output,name,'current.json'),JSON.stringify({preserved:name}));}
 const result=spawnSync(process.execPath,['--max-old-space-size=2048',join(scripts,'build-windows.mjs'),'--acceptance','--no-bundle','--preserve-output','--target=x86_64-pc-windows-msvc'],{cwd:app,windowsHide:true,encoding:'utf8',timeout:30000,env:{...process.env,NODE_OPTIONS:'--max-old-space-size=2048',CARGO_TARGET_DIR:target,NANFENG_AI_WINDOWS_OUTPUT_DIR:output}});
 assert.equal(result.status,0,result.stderr);
 const receipt=JSON.parse(await readFile(join(output,'windows-acceptance-release/current.json'),'utf8'));
 assert.equal(receipt.buildProfile,'release');assert.equal(receipt.identifier,'com.nanzhufeng.ai.desktop.windowsacceptance');
 assert.equal(receipt.sha256,createHash('sha256').update(bytes).digest('hex').toUpperCase());assert.deepEqual(await readFile(receipt.executable),bytes);
 for(const name of ['windows-user','windows-acceptance'])assert.deepEqual(JSON.parse(await readFile(join(output,name,'current.json'),'utf8')),{preserved:name});
});

test('Release retention never removes Debug candidates and retains running Release candidates',async()=>{
 const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../.runtime-qa/release-retention-tests',randomUUID());
 const old='A'.repeat(64),current='B'.repeat(64),active='C'.repeat(64);
 for(const name of ['windows-acceptance','windows-acceptance-release'])for(const hash of [old,current,active]){
  const p=join(root,name,hash);await mkdir(p,{recursive:true});await writeFile(join(p,'fixture.bin'),'synthetic lifecycle sample');await utimes(p,new Date('2000-01-01'),new Date('2000-01-01'));
 }
 const result=await pruneWindowsReleaseAcceptanceOutput(root,current,{activeHashes:new Set([active])});assert.equal(result.removed,1);
 for(const hash of [old,current,active])await access(join(root,'windows-acceptance',hash,'fixture.bin'));
 for(const hash of [current,active])await access(join(root,'windows-acceptance-release',hash,'fixture.bin'));
 await assert.rejects(access(join(root,'windows-acceptance-release',old)));
});
