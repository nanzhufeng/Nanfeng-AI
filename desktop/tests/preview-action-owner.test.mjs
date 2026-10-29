import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
function harness(action,nextAction) {
 let resolve,reject;const gate=new Promise((yes,no)=>{resolve=yes;reject=no;});const calls=[],feedback=[],errors=[];
 const preview={workspaceId:'w',attachmentId:'a',text:'准确原文',displayName:'original.txt'};
 const chat={dataArea:'CHAT',textPreview:preview,error:'old'},work={dataArea:'WORK',textPreview:{...preview,text:'另一文件'},error:'new WORK error'};
 const context=vm.createContext({state:chat,event:{preventDefault(){}},navigator:{clipboard:{writeText:async text=>{calls.push(text);await gate;}}},
  showCopyIconFeedback:target=>feedback.push(target),showPreviewActionError:(target,message)=>errors.push(message),
  activePreviewForKind:()=>context.state.textPreview,sharePreviewAttachment:async p=>{calls.push(p);await gate;},saveAttachmentCopy:async()=>gate});
 const a=source.indexOf(`  if (action === '${action}') {`),b=source.indexOf(`  if (action === '${nextAction}')`,a);
 assert.ok(a>=0&&b>a);vm.runInContext(`globalThis.run=async target=>{const action='${action}';${source.slice(a,b)}}`,context);
 const target={dataset:{previewKind:'text'},closest:()=>null};
 return {context,chat,work,preview,calls,feedback,errors,resolve,reject,run:()=>context.run(target)};
}
test('late copy after an area switch cannot clear WORK feedback or flash an old target',async()=>{
 const h=harness('copy-text-preview','chat-attachment-search');const copying=h.run();h.context.state=h.work;h.resolve();await copying;
 assert.deepEqual(h.calls,['准确原文']);assert.equal(h.work.error,'new WORK error');assert.equal(h.feedback.length,0);
});
test('same-area file replacement rejects a late copy result',async()=>{
 const h=harness('copy-text-preview','chat-attachment-search');const copying=h.run();h.chat.textPreview={...h.preview,attachmentId:'next'};h.chat.error='new file error';h.resolve();await copying;
 assert.equal(h.chat.error,'new file error');assert.equal(h.feedback.length,0);
});
test('late share result and rejection cannot repaint a later preview',async()=>{
 for(const failed of [false,true]){
  const h=harness('share-preview-attachment','close-video-preview');const sharing=h.run();h.context.state=h.work;
  if(failed)h.reject(Error('old share failed'));else h.resolve();await sharing;
  assert.equal(h.work.error,'new WORK error');assert.equal(h.errors.length,0);assert.equal(h.calls[0],h.preview);
 }
});
test('a late export error is kept out of the replacement preview',async()=>{
 const h=harness('save-preview-attachment','share-preview-attachment');const saving=h.run();h.chat.textPreview={...h.preview,attachmentId:'next'};h.reject(Error('old export failed'));await saving;assert.equal(h.errors.length,0);
});
test('current copy uses the captured original text and acknowledges only its own preview',async()=>{
 const h=harness('copy-text-preview','chat-attachment-search');const copying=h.run();h.resolve();await copying;assert.deepEqual(h.calls,['准确原文']);assert.equal(h.feedback.length,1);assert.equal(h.chat.error,'');
});

test('a ready empty text preview copies the exact empty string and acknowledges completion',async()=>{
 const h=harness('copy-text-preview','chat-attachment-search');h.preview.text='';
 const copying=h.run();h.resolve();await copying;
 assert.deepEqual(h.calls,['']);assert.equal(h.feedback.length,1);assert.equal(h.chat.error,'');
});

for(const blocked of [{loading:true},{error:'读取失败'}])test('an unavailable text preview cannot copy retained text',async()=>{
 const h=harness('copy-text-preview','chat-attachment-search');Object.assign(h.preview,blocked);
 const copying=h.run();h.resolve();await copying;
 assert.deepEqual(h.calls,[]);assert.equal(h.feedback.length,0);assert.equal(h.chat.error,'old');
});
test('an authorized export retains its original file after a delayed picker without clearing a new preview error',async()=>{
 const a=source.indexOf('async function saveAttachmentCopy('),b=source.indexOf('function activePreviewForKind(',a);let release;const picker=new Promise(resolve=>release=resolve);const calls=[];
 const original={attachmentId:'a'},chat={dataArea:'CHAT',textPreview:original,status:'new status',error:'new preview error'};
 const context=vm.createContext({state:chat,native:true,safeAttachmentSaveName:name=>name,dialogInvoke:()=>picker,invokePreviewOwner:async(owner,command,args)=>calls.push({owner,command,args})});
 vm.runInContext(source.slice(a,b),context);const saving=context.saveAttachmentCopy({workspaceId:'original-workspace',attachmentId:'original-file',displayName:'original.txt'},{isCurrent:()=>chat.textPreview===original});
 chat.textPreview={attachmentId:'next'};release('C:/synthetic-owned/original.txt');assert.equal(await saving,true);
 assert.equal(calls[0].owner,chat);assert.equal(calls[0].args.args.attachmentId,'original-file');assert.equal(calls[0].args.args.workspaceId,'original-workspace');assert.equal(chat.error,'new preview error');assert.equal(chat.status,'new status');
});
