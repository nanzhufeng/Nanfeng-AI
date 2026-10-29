import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { resolve, join, sep } from 'node:path';
const sourceText=await readFile(new URL('../scripts/monitor-windows-local-stability-native.mjs',import.meta.url),'utf8');
for(const reopen of [false,true])test(`stability ${reopen?'cold':'first'} launch pins its original immutable executable when current pointer changes`,async()=>{
 const begin=sourceText.indexOf('async function start(reopen = false) {'),end=sourceText.indexOf('function stop()',begin);
 const rootParent=resolve('D:/CodexTemp/test-stability-pin'),root=join(rootParent,'nanfeng-ai-windows-acceptance.test');const source=resolve('D:/artifacts/old/NanfengAI-Windows-Acceptance.exe'),runningExecutable=resolve('D:/artifacts/old/nanfeng-ai-desktop-spike.exe');let args;
 const context={assert,resolve,join,sep,rootParent,root,source,runningExecutable,receipt:{sha256:'OLD',identifier:'com.nanzhufeng.ai.desktop.windowsacceptance'},result:{},launch:null,reservePort:async()=>7000,readFile:async()=>JSON.stringify({identifier:'com.nanzhufeng.ai.desktop.windowsacceptance'}),startProcess:async passed=>{args=passed;const pinned=passed[passed.indexOf('-ExecutablePath')+1]===source;return {root,executable:runningExecutable,sha256:pinned?'OLD':'NEW_CURRENT'};}};
 const start=vm.runInNewContext(sourceText.slice(begin,end)+';start',context);await start(reopen);assert.equal(args[args.indexOf('-ExecutablePath')+1],source);assert.equal(args.includes('-Root'),reopen);
});

for(const invalid of [false,true])test(`cleanup ${invalid?'rejects unowned path':'uses actual owned launch identity after candidate mismatch'}`,()=>{
 const begin=sourceText.indexOf('function stop() {'),end=sourceText.indexOf('async function cdp',begin);const desktopRoot=resolve('D:/repo/desktop'),rootParent=resolve('D:/CodexTemp/test-stability-pin'),root=join(rootParent,'nanfeng-ai-windows-acceptance.test');const sha256='B'.repeat(64);const executable=resolve(desktopRoot,'output/windows-acceptance-binaries',sha256,'nanfeng-ai-desktop-spike.exe');let command;
 const context={assert,resolve,join,sep,desktopRoot,rootParent,root,receipt:{identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256:'A'.repeat(64)},launch:{pid:123,identifier:'com.nanzhufeng.ai.desktop.windowsacceptance',sha256,root,executable:invalid?resolve('D:/other.exe'):executable,webviewProfile:join(root,'webview2')},pids:[],quote:v=>"'"+v+"'",ps:v=>command=v};
 const stop=vm.runInNewContext(sourceText.slice(begin,end)+';stop',context);
 if(invalid){assert.throws(stop,/owned staged executable/);assert.equal(command,undefined);}else{stop();assert.ok(command.includes(sha256));assert.ok(command.includes(executable));assert.ok(!command.includes('A'.repeat(64)));assert.equal(context.launch,null);assert.deepEqual(context.pids,[123]);}
});
