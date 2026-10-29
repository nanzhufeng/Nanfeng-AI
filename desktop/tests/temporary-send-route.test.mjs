import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
const send=source.slice(source.indexOf('async function sendLocalMessage()'),source.indexOf('saveLocalMessage = sendLocalMessage;'));
function harness({holdDraft=false,holdAppend=false}={}){
 let releaseDraft,releaseAppend;const calls=[];
 const state={composerDraft:'original temporary text',composerAttachments:[],temporaryConversation:{temporaryId:'temp-one',draftAttachmentIds:[]}};
 const context=vm.createContext({state,render(){},Date,handleExpiredTemporary:()=>false,
  updateTemporaryDraft:()=>holdDraft?new Promise(r=>{releaseDraft=r;}):Promise.resolve(),
  scheduleTemporaryDraft(value){calls.push(['retain',value]);},
  invoke:async(command,args)=>{calls.push([command,args]);const result={temporaryId:'temp-one',draft:'',draftAttachmentIds:[],messages:[{text:'original temporary text'}]};return holdAppend?new Promise(r=>{releaseAppend=()=>r(result);}):result;}});
 vm.runInContext(send,context);
 return {state,calls,send:()=>vm.runInContext('sendLocalMessage()',context),releaseDraft:()=>releaseDraft(),releaseAppend:()=>releaseAppend()};
}
test('leaving temporary chat while its draft saves prevents append and keeps normal input',async()=>{
 const h=harness({holdDraft:true});const sending=h.send();
 h.state.temporaryConversation=null;h.state.composerDraft='new normal input';h.releaseDraft();await sending;
 assert.equal(h.calls.length,0);assert.equal(h.state.composerDraft,'new normal input');
});
test('late temporary append cannot reopen TEMP or erase the normal draft',async()=>{
 const h=harness({holdAppend:true});const sending=h.send();await new Promise(r=>setImmediate(r));
 h.state.temporaryConversation=null;h.state.composerDraft='new normal input';h.releaseAppend();await sending;
 assert.equal(h.state.temporaryConversation,null);assert.equal(h.state.composerDraft,'new normal input');
});
test('editing during temporary draft save requires a fresh Send press',async()=>{
 const h=harness({holdDraft:true});const sending=h.send();h.state.composerDraft='new temporary input';h.releaseDraft();await sending;
 assert.equal(h.calls.length,0);assert.equal(h.state.composerDraft,'new temporary input');
});
test('editing while append returns keeps newer text as a draft',async()=>{
 const h=harness({holdAppend:true});const sending=h.send();await new Promise(r=>setImmediate(r));
 h.state.composerDraft='next temporary input';h.releaseAppend();await sending;
 assert.equal(h.state.composerDraft,'next temporary input');assert.equal(h.state.temporaryConversation.draft,'next temporary input');
 assert.deepEqual(h.calls.at(-1),['retain','next temporary input']);
});
