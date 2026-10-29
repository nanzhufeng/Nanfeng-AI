import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const body=source.slice(source.indexOf('function preserveSearchInputFocus()'),source.indexOf('function focusSearchAfterHistoryDismissal()'));
const turn=()=>new Promise(setImmediate);
function harness(){
 const input={id:'full-search-input',selectionStart:2,selectionEnd:5,selectionDirection:'backward'};
 const document={activeElement:input,body:{},getElementById:()=>next};
 const next={isConnected:true,focus(){document.activeElement=this},setSelectionRange(...args){this.selection=args}};
 const state={searchPanel:true,dialog:null};const context=vm.createContext({document,state,queueMicrotask});vm.runInContext(body,context);
 return {context,document,state,next,capture(){context.preserveSearchInputFocus();document.activeElement=document.body;}};
}
test('search rerender preserves focus and selection of the replaced input',async()=>{const h=harness();h.capture();await turn();assert.equal(h.document.activeElement,h.next);assert.deepEqual(h.next.selection,[2,5,'backward']);});
test('late focus restoration cannot steal focus after changing area',async()=>{const h=harness();h.capture();h.context.state={searchPanel:true,dialog:null};await turn();assert.equal(h.document.activeElement,h.document.body);});
test('a newly opened dialog keeps focus ownership',async()=>{const h=harness();h.capture();h.state.dialog={kind:'editor'};await turn();assert.equal(h.document.activeElement,h.document.body);});
test('focus deliberately moved to another control is kept',async()=>{const h=harness();h.capture();const other={id:'other'};h.document.activeElement=other;await turn();assert.equal(h.document.activeElement,other);});
