import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

assert.equal(process.platform,'win32');
assert.ok(process.execArgv.includes('--max-old-space-size=2048'));
assert.match(process.env.NFAI_BOUNDED_JOB_NAME||'',/^NanfengAIBounded-[0-9a-f-]{36}$/);
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..'),repo=resolve(desktop,'..');
const stage=join(repo,'docs/review/20261017-windows-vctip-lifecycle');
const root=join(repo,'.runtime-qa','msvc-child-lifetime-'+randomUUID());await mkdir(root);
await writeFile(join(root,'.nanfeng-msvc-probe.json'),JSON.stringify({purpose:'synthetic compiler child lifetime; never execute generated EXE'}));
const tools='C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64';
const py='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const source=join(root,'probe.c'),object=join(root,'probe.obj'),exe=join(root,'probe.exe');
await writeFile(source,'void nfai_entry(void) {}\n');
const rustc=execFileSync('rustup',['which','rustc'],{encoding:'utf8',windowsHide:true,timeout:5000}).trim();
const rustSource=join(root,'probe.rs');await writeFile(rustSource,'fn main() {}\n');
const commands=[['cl.exe','/nologo','/c','/GS-','/TC','/Fo'+object,source],['link.exe','/nologo','/NODEFAULTLIB','/ENTRY:nfai_entry','/SUBSYSTEM:CONSOLE','/OUT:'+exe,object],['rustc.exe','--target','x86_64-pc-windows-msvc','--edition=2021','--crate-name','nfai_toolchain_probe','-C','opt-level=2','-o',join(root,'probe-rust.exe'),rustSource]];
const result={root,applicationRecompiled:false,generatedExeExecuted:false,machineConfigurationChanged:false,steps:[],samples:[]};
const observe=()=>JSON.parse(execFileSync(py,['-X','utf8',join(desktop,'scripts/read-msvc-probe-job.py')],{encoding:'utf8',windowsHide:true,timeout:5000}));
try{
 for(const [tool,...args] of commands){const path=tool==='rustc.exe'?rustc:join(tools,tool);const started=Date.now();const output=execFileSync(path,args,{encoding:'utf8',windowsHide:true,cwd:root,timeout:10000});result.steps.push({tool,exitCode:0,elapsedMs:Date.now()-started,toolSha256:createHash('sha256').update(await readFile(path)).digest('hex'),output:output.trim()});}
 const started=Date.now();let seen=false,alive=false;
 do{const rows=observe();const helpers=rows.filter(r=>r.inOwnedJob&&r.imagePath?.toLowerCase().endsWith('\\vctip.exe'));alive=helpers.length>0;seen||=alive;result.samples.push({elapsedMs:Date.now()-started,helpers});if(!alive)break;await new Promise(done=>setTimeout(done,250));}while(Date.now()-started<15000);
 result.vctipSeen=seen;result.helperStillAliveAtObservationEnd=alive;result.observationMs=Date.now()-started;result.originalOneSecondGateWouldFail=result.samples.some(s=>s.elapsedMs>=1000&&s.helpers.length>0);result.naturalExitObserved=seen&&!alive;
 // A persistent helper remains a diagnostic failure; the unchanged parent
 // Job gate performs its normal scoped cleanup after this root exits.
 if(alive)process.exitCode=23;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{await writeFile(join(stage,'minimal-compiler-observation.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({root,vctipSeen:result.vctipSeen,naturalExitObserved:result.naturalExitObserved,stillAlive:result.helperStillAliveAtObservationEnd,error:result.error}));}
