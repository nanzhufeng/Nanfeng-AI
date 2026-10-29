import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdir,readFile,writeFile,rename,stat,copyFile} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {join,resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
assert.equal(process.platform,'win32');assert.ok(process.env.NFAI_BOUNDED_JOB_NAME?.startsWith('NanfengAIBounded-'));
const desktop=resolve(dirname(fileURLToPath(import.meta.url)),'..'),repo=resolve(desktop,'..');
const py='C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const tools='C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64';
const stage=join(repo,'docs/review/20261018-windows-vctip-instance-timing');
const cache=resolve(desktop,'src-tauri/target/nanfeng-msvc-link-owner');await mkdir(cache,{recursive:true});
const hash=async p=>createHash('sha256').update(await readFile(p)).digest('hex');
const paths={source:join(desktop,'scripts/windows-msvc-owned-linker.rs'),owner:join(desktop,'scripts/windows-msvc-owned-linker.py'),ready:join(desktop,'scripts/read-owned-vctip-readiness.py'),compilerEnv:join(desktop,'scripts/read-windows-msvc-environment.py')};
const signatures=JSON.parse(execFileSync('pwsh',['-NoProfile','-NonInteractive','-Command',"@('"+join(tools,'link.exe')+"','"+join(tools,'vctip.exe')+"','"+join(tools,'cl.exe')+"')|ForEach-Object {(Get-AuthenticodeSignature -LiteralPath $_).Status.ToString()}|ConvertTo-Json -Compress"],{encoding:'utf8',windowsHide:true}));assert.deepEqual(signatures,['Valid','Valid','Valid']);
const identities={sourceSha256:await hash(paths.source),ownerSha256:await hash(paths.owner),readySha256:await hash(paths.ready),compilerEnvSha256:await hash(paths.compilerEnv),linkSha256:await hash(join(tools,'link.exe')),vctipSha256:await hash(join(tools,'vctip.exe')),clSha256:await hash(join(tools,'cl.exe'))};
const env={...process.env,NFAI_MSVC_LINK_PYTHON:py,NFAI_MSVC_LINK_OWNER_SCRIPT:paths.owner,NFAI_MSVC_REAL_LINK_SHA256:identities.linkSha256,NFAI_MSVC_VCTIP_SHA256:identities.vctipSha256,NFAI_MSVC_CL_SHA256:identities.clSha256,NFAI_MSVC_LINK_RECEIPTS:join(stage,'cpp-owned-receipts')};
env.NFAI_MSVC_COMPILER_ENV_SHA256=identities.compilerEnvSha256;
if(process.argv.includes('--wait-idle')){
 const guardOutput=execFileSync(py,['-X','utf8',paths.owner,'--wait-idle'],{env,windowsHide:true,encoding:'utf8',timeout:460000});
 await writeFile(join(stage,'owner-idle-queue.json'),guardOutput);assert.equal(JSON.parse(guardOutput).guardReleased,true);
 console.log(guardOutput.trim());
}
for(const [key,path] of Object.entries({...paths,link:join(tools,'link.exe'),vctip:join(tools,'vctip.exe'),cl:join(tools,'cl.exe')})){
 assert.equal(await hash(path),identities[key+'Sha256'],'Inputs changed while waiting for compiler guard: '+key);
}
const target=join(cache,'link.exe'),manifest=join(cache,'owner-manifest.json');
const prior=await readFile(manifest,'utf8').then(JSON.parse).catch(error=>{if(error.code==='ENOENT')return null;throw error});
if(prior){assert.equal(await hash(target),prior.shimSha256,'Existing cache does not match owned manifest');}
else assert.equal(await stat(target).then(()=>true).catch(()=>false),false,'Unknown linker cache is not overwritten');
if(!prior||Object.entries(identities).some(([k,v])=>prior[k]!==v)){
 const pending=join(cache,'link.pending.exe');
 assert.equal(await stat(pending).then(()=>true).catch(()=>false),false,'Inspect existing pending linker before rebuilding');
 execFileSync(py,['-X','utf8',paths.owner,'--bootstrap',pending],{env,windowsHide:true,stdio:'inherit',timeout:45000});
 if(prior){
  const saved=join(cache,'link-'+prior.shimSha256+'.exe');
  if(await stat(saved).then(()=>true).catch(()=>false))assert.equal(await hash(saved),prior.shimSha256);
  else await copyFile(target,saved);
 }
 await rename(pending,target);
 const value={...identities,shimSha256:await hash(target),toolScope:'owned Windows acceptance compile only',createdAt:new Date().toISOString()};
 await writeFile(manifest+'.pending',JSON.stringify(value,null,2));await rename(manifest+'.pending',manifest);
}
const compiler=join(cache,'cl.exe');
if(await stat(compiler).then(()=>true).catch(()=>false)){
 const existing=await hash(compiler);
 assert.ok(existing===await hash(target)||existing===prior?.shimSha256,'Unknown compiler shim is not overwritten');
 if(existing!==await hash(target))await copyFile(target,compiler);
}else await copyFile(target,compiler);
const root=join(repo,'.runtime-qa','msvc-link-shim-'+randomUUID());await mkdir(root);await writeFile(join(root,'.nanfeng-tool-probe.json'),JSON.stringify({synthetic:true,kind:'msvc-link-shim'}));
const spaced=join(root,'空格 & 参数');await mkdir(spaced);
const source=join(spaced,'example.rs'),output=join(spaced,'未执行示例.exe');await writeFile(source,'fn main() { println!("synthetic link probe"); }\n');
execFileSync('C:/Users/Administrator/.cargo/bin/rustc.exe',['--edition=2021','--crate-name','nfai_msvc_link_owner_probe','--target','x86_64-pc-windows-msvc','-C','linker='+target,source,'-o',output],{env,windowsHide:true,stdio:'inherit',timeout:45000});
assert.ok((await stat(output)).size>10000);
const crate=join(spaced,'cc-probe');await mkdir(crate);await mkdir(join(crate,'src'));
await writeFile(join(crate,'Cargo.toml'),'[package]\nname="nfai_owned_cc_probe"\nversion="0.0.0"\nedition="2021"\nbuild="build.rs"\n[build-dependencies]\ncc="=1.4.2"\nfind-msvc-tools="=0.1.10"\n');
await writeFile(join(crate,'src/lib.rs'),'pub fn marker() -> bool { true }\n');
await writeFile(join(crate,'build.rs'),'fn main() { cc::Build::new().cpp(true).file("probe.cpp").compile("nfai_cc_probe"); }\n');
await writeFile(join(crate,'probe.cpp'),'#include <Windows.h>\nextern "C" unsigned long nfai_cc_probe() { return GetCurrentProcessId(); }\n');
execFileSync('C:/Users/Administrator/.cargo/bin/cargo.exe',['build','--offline','--manifest-path',join(crate,'Cargo.toml'),'--target','x86_64-pc-windows-msvc','--target-dir',resolve(cache,'..'),'--jobs','1'],{env:{...env,CC:compiler,CXX:compiler,CARGO_TARGET_X86_64_PC_WINDOWS_MSVC_LINKER:target},windowsHide:true,stdio:'inherit',timeout:240000});
const longCompile=JSON.parse(execFileSync(py,['-X','utf8',paths.owner,'--sqlite-compile-self-test'],{env,windowsHide:true,encoding:'utf8',timeout:90000}).trim().split(/\r?\n/).at(-1));
assert.equal(longCompile.longSqliteCompilePassed,true);await writeFile(join(stage,'long-sqlite-compile-self-test.json'),JSON.stringify(longCompile,null,2));
let negativeRejected=false;
try{execFileSync(target,['/nologo'],{env:{...env,NFAI_MSVC_REAL_LINK_SHA256:'0'.repeat(64)},windowsHide:true,stdio:'ignore',timeout:5000});}catch(error){assert.equal(error.status,1);negativeRejected=true;}
assert.ok(negativeRejected);
const result={pass:true,root,cache,target,compiler,shimSha256:await hash(target),...identities,unicodeSpacesAndAmpersandPreserved:true,stdlibLinkPassed:true,cppWindowsSdkCompilePassed:true,longSqliteCompilePassed:true,wrongToolHashRejected:true,generatedExeExecuted:false,applicationRecompiled:false,globalEnvironmentChanged:false};
await writeFile(join(stage,'owned-linker-self-test.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
