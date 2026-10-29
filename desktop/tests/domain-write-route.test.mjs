import { recordDomainConflict } from '../src/domain-editor-conflict.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const mutate=source.slice(source.indexOf('async function mutate(args,'),source.indexOf('function saveProject()',source.indexOf('async function mutate(args,')));
const undo=source.slice(source.indexOf('async function undo(redo')).split(/\n(?=(?:async )?function )/)[0];
function harness({readWorkspace} = {}) {
 let resolve,reject,refreshes=0;
 const owner={current:{summary:{id:'CHAT-workspace'}},dialog:{kind:'editor'},error:'',status:''};
 const context=vm.createContext({recordDomainConflict,state:owner,native:true,intent:kind=>kind,render(){},refresh:async()=>refreshes++,
  invoke:command=>command==='read_desktop_workspace' ? readWorkspace() : new Promise((yes,no)=>{resolve=yes;reject=no;})});
 vm.runInContext(mutate+undo,context);
 return {context,owner,call:code=>vm.runInContext(code,context),resolve:()=>resolve(),reject:(error=Object.assign(Error('old area'),{code:'STALE_DATA_AREA'}))=>reject(error),get refreshes(){return refreshes;}};
}
for(const command of ["mutate({entity:'project'},'saved')",'undo()','undo(true)']) {
 test(`${command}: old area receipt cannot replace current error`,async()=>{
  const h=harness();const write=h.call(command);const work={current:{summary:{id:'WORK-workspace'}},error:'WORK current',status:'WORK',dialog:{kind:'other'}};
  h.context.state=work;h.reject();await write;assert.equal(work.error,'WORK current');assert.equal(h.refreshes,0);
 });
}
test('a saved project receipt does not close an editor opened while it was saving',async()=>{
 const h=harness();const write=h.call("mutate({entity:'project'},'saved')");const newer={kind:'new editor'};h.owner.dialog=newer;h.resolve();await write;
 assert.equal(h.owner.dialog,newer);assert.equal(h.owner.status,'saved');assert.equal(h.refreshes,1);
});
test('a valid current mutation still clears its editor and refreshes',async()=>{
 const h=harness();const write=h.call("mutate({entity:'project'},'saved')");h.resolve();await write;assert.equal(h.owner.dialog,null);assert.equal(h.refreshes,1);
});

test('revision conflict reads current workspace without closing the unsaved editor',async()=>{
 const latest={summary:{id:'CHAT-workspace'},exchange:{memory:[{id:'m',revision:2,body:'latest'}]}};
 const h=harness({readWorkspace:async()=>latest});const editor=h.owner.dialog;editor.value='unsaved';
 const write=h.call("mutate({entity:'memory'},'saved')");h.reject(Error('REVISION_CONFLICT'));await write;
 assert.equal(h.owner.current,latest);assert.equal(h.owner.dialog,editor);assert.equal(editor.value,'unsaved');assert.match(h.owner.error,/REVISION_CONFLICT/);
});
test('late conflict version read cannot replace a new data area',{timeout:1000},async()=>{
 let resolveRead, signalReading;const started=new Promise(resolve=>resolveRead=resolve);const reading=new Promise(resolve=>signalReading=resolve);
 const h=harness({readWorkspace:()=>{signalReading();return started}});
 const write=h.call("mutate({entity:'memory'},'saved')");h.reject(Error('REVISION_CONFLICT'));await reading;
 const work={current:{summary:{id:'WORK-workspace'}},error:'WORK current'};h.context.state=work;
 resolveRead({summary:{id:'CHAT-workspace'}});await write;assert.equal(work.current.summary.id,'WORK-workspace');assert.equal(work.error,'WORK current');
});
