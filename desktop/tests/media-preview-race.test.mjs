import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
test('late video response cannot resurrect a closed preview', async () => {
  const source=await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
  const body=source.slice(source.indexOf('async function openVideoPreview('),source.indexOf('async function closeVideoPreview('));
  let resolve;
  const pending=new Promise(done=>{resolve=done;});
  const state={current:{summary:{id:'workspace'}}};
  const context=vm.createContext({native:true,state,render(){},invoke:()=>pending,queueMicrotask(){throw new Error('closed preview scheduled playback');}});
  vm.runInContext(body,context);
  const opening=context.openVideoPreview('video');
  state.videoPreview=null;
  resolve({attachmentId:'video',mediaUrl:'fixture',durationMillis:2000,byteCount:100});
  await opening;
  assert.equal(state.videoPreview,null);
});
