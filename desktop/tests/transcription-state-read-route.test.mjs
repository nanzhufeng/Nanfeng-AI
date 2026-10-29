import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const src=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');const block=src.slice(src.indexOf('async function loadTranscriptionState('),src.indexOf('function transcriptionTask('));
function harness(){const pending=[];const state={transcription:{tasks:[{id:'current'}]},selectedTranscriptionTaskId:'current'};
 const context=vm.createContext({state,native:true,cloudOpenAreaGeneration:0,invoke:()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))});vm.runInContext(block,context);
 return {state,context,pending,load:()=>context.loadTranscriptionState(state)};}
test('a later transcription refresh wins over an older snapshot',async()=>{
 const h=harness();const a=h.load(),b=h.load();h.pending[1].resolve({tasks:[{id:'new'}]});await b;h.pending[0].resolve({tasks:[{id:'old'}]});await a;
 assert.equal(h.state.transcription.tasks[0].id,'new');assert.equal(h.state.selectedTranscriptionTaskId,'new');
});
test('same owner returning after an area round trip does not accept its old transcription snapshot',async()=>{
 const h=harness();const a=h.load();h.context.cloudOpenAreaGeneration+=2;h.pending[0].resolve({tasks:[{id:'old'}]});await a;assert.equal(h.state.selectedTranscriptionTaskId,'current');
});
