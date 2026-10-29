import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');const start=source.indexOf("  if (action === 'open-source-link') {");const end=source.indexOf("  if (action === 'show-assistant-answer-information'",start);assert.ok(start>=0&&end>start);const body=source.slice(start,end);
const factory=new Function('invoke','render','initialState',`let state=initialState;return {run:async (event,target)=>{const action='open-source-link';${body}},setOwner:next=>state=next};`);
test('actual delegated link handler prevents navigation and sends exactly the selected public URL through its captured owner',async()=>{
 for(const href of ['https://github.com/nanzhufeng/Nanfeng-AI','https://github.com/nanzhufeng/Nanfeng-AI/releases','https://github.com/nanzhufeng/Nanfeng-AI/commit/2873f051cbea91c0122f14a08f807f374b28da75']){
  const owner={dataArea:'CHAT',error:''};const calls=[];let prevented=0;const handler=factory(async(...args)=>calls.push(args),()=>{},owner);await handler.run({preventDefault(){prevented++;}},{dataset:{href}});assert.equal(prevented,1);assert.deepEqual(calls,[['open_desktop_source_link',{args:{href}},owner]]);
 }
});
test('actual delegated failure gives actionable Chinese browser guidance and renders it',async()=>{
 const owner={dataArea:'CHAT',error:''};let renders=0;const handler=factory(async()=>{throw new Error('合成默认浏览器未设置');},()=>renders++,owner);await handler.run({preventDefault(){}},{dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}});assert.match(owner.error,/无法打开链接/);assert.match(owner.error,/请检查默认浏览器设置后重试/);assert.equal(renders,1);
});
test('late browser failure belongs to its old area and cannot pollute the newly selected owner',async()=>{
 let reject;const owner={dataArea:'CHAT',error:''},next={dataArea:'WORK',error:''};let renders=0;const handler=factory(()=>new Promise((_,fail)=>reject=fail),()=>renders++,owner);const pending=handler.run({preventDefault(){}},{dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}});handler.setOwner(next);reject(new Error('late synthetic failure'));await pending;assert.equal(next.error,'');assert.equal(owner.error,'');assert.equal(renders,0);
});

test('successful retry clears and renders only its own previous browser failure',async()=>{
 const owner={dataArea:'CHAT',error:''};let refuse=true,renders=0;const handler=factory(async()=>{if(refuse)throw new Error('synthetic browser refusal');},()=>renders++,owner);const target={dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}};await handler.run({preventDefault(){}},target);assert.ok(owner.sourceLinkFailure);refuse=false;await handler.run({preventDefault(){}},target);assert.equal(owner.error,'');assert.equal(owner.sourceLinkFailure,null);assert.equal(renders,2);
});
test('successful source handoff cannot clear an unrelated business error',async()=>{
 const owner={dataArea:'CHAT',error:'settings write failed'};let renders=0;const handler=factory(async()=>{},()=>renders++,owner);await handler.run({preventDefault(){}},{dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}});assert.equal(owner.error,'settings write failed');assert.equal(renders,0);
});
test('an older successful retry cannot clear a newer identical source failure',async()=>{
 const owner={dataArea:'CHAT',error:''};let mode='fail',release,renders=0;const handler=factory(()=>mode==='hold'?new Promise(done=>release=done):Promise.reject(new Error('same browser failure')),()=>renders++,owner);const event={preventDefault(){}},target={dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}};await handler.run(event,target);mode='hold';const pending=handler.run(event,target);mode='fail';await handler.run(event,target);const latest=owner.sourceLinkFailure;release();await pending;assert.equal(owner.sourceLinkFailure,latest);assert.equal(owner.error,latest.message);assert.equal(renders,2);
});

test('an older failed handoff cannot overwrite a newer successful request',async()=>{
 const owner={dataArea:'CHAT',error:''};let reject,hold=true,renders=0;const handler=factory(()=>hold?new Promise((_,fail)=>reject=fail):Promise.resolve(),()=>renders++,owner);const event={preventDefault(){}},target={dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}};const old=handler.run(event,target);hold=false;await handler.run(event,target);reject(new Error('obsolete failure'));await old;assert.equal(owner.error,'');assert.equal(renders,0);
});

for(const change of ['page','visit','conversation','dialog'])test(`late source failure cannot replace feedback after a ${change} change`,async()=>{
 const owner={dataArea:'CHAT',pane:'settings',settingsSection:'about',settingsReadGeneration:1,selectedConversationId:'old',dialog:null,error:''};let reject,renders=0;
 const handler=factory(()=>new Promise((_,fail)=>reject=fail),()=>renders++,owner);
 const pending=handler.run({preventDefault(){}},{dataset:{href:'https://github.com/nanzhufeng/Nanfeng-AI'}});
 if(change==='page')owner.settingsSection='model-configuration';
 if(change==='visit')owner.settingsReadGeneration=3;
 if(change==='conversation')owner.selectedConversationId='later';
 if(change==='dialog')owner.dialog={kind:'later-dialog'};
 owner.error='后来页面的明确错误';reject(new Error('旧浏览器回执失败'));await pending;
 assert.equal(owner.error,'后来页面的明确错误');assert.equal(owner.sourceLinkFailure,undefined);assert.equal(renders,0);
});
