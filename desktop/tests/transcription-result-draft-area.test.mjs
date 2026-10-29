import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const start=source.indexOf('async function continueChatWithTranscription(');
const handler=source.slice(start,source.indexOf('\nconst loadUtilityPage',start));
function setup(area='CHAT'){
 const owner={dataArea:area,temporaryConversation:false,error:'',status:''};const calls=[],paints=[];let saved;
 const ctx={state:owner,tauriBridge:{},transcriptionTask:()=>({workspaceId:'workspace',resultAttachmentId:'result'}),flushOrdinaryComposerDraft:async()=>{},
 invoke:async(name,args,who)=>{calls.push({name,who:who||ctx.state});return name==='read_desktop_text_preview'?{attachmentId:'result'}:{text:'保留草稿',attachments:[{id:'existing'}],modelId:'model',toneOverride:'tone',webSearchOverride:false};},
 persistOrdinaryComposerDraft:async args=>{assert.equal(args.dataArea,area);saved=args;return {text:args.text,attachments:args.attachmentIds.map(id=>({id})),expiresAtMs:1};},
 ensureConversationActionWorkspace:async()=>true,resetComposerPresentation(){},armNewConversationDraftExpiry(){},render:()=>paints.push(ctx.state)};
 ctx.invokeForAuthorizedDataAreaOperation=(_bridge,name,args,who)=>ctx.invoke(name,args,who);
 vm.runInNewContext(handler,ctx);return {ctx,owner,calls,paints,saved:()=>saved};
}
for(const area of ['CHAT','WORK'])test('OCR result draft uses '+area+' owner and preserves draft',async()=>{const t=setup(area);await t.ctx.continueChatWithTranscription('task');assert.ok(t.saved());assert.equal(t.saved().text,'保留草稿');assert.deepEqual(Array.from(t.saved().attachmentIds),['existing','result']);assert.equal(t.saved().webSearchOverride,false);assert.equal(t.saved().modelId,'model');assert.equal(t.saved().toneOverride,'tone');});
for(const reject of [false,true])test('late OCR draft '+(reject?'failure':'success')+' does not project onto another area',async()=>{const t=setup();let settle;const p=new Promise((ok,no)=>{settle=reject?no:ok});p.catch(()=>{});t.ctx.persistOrdinaryComposerDraft=async args=>{assert.equal(args.dataArea,'CHAT');return p;};const running=t.ctx.continueChatWithTranscription('task');for(let n=0;n<8;n++)await Promise.resolve();const other={dataArea:'WORK',status:'other',error:'other',temporaryConversation:false};t.ctx.state=other;settle(reject?new Error('synthetic'):{text:'保留草稿',attachments:[{id:'result'}]});await running;assert.deepEqual(other,{dataArea:'WORK',status:'other',error:'other',temporaryConversation:false});assert.ok(!t.paints.includes(other));});
