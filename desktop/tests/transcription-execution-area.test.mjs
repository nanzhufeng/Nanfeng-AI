import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const start = source.indexOf('async function executeTranscriptionTask(');
const handler = source.slice(start, source.indexOf('\nasync function previewTranscriptionSource(', start));
for (const rejects of [false, true]) {
  test(`late OCR ${rejects ? 'failure' : 'success'} leaves the other area untouched`, async () => {
    let settle;
    const pending = new Promise((resolve, reject) => { settle = rejects ? reject : resolve; });
    const chat = { transcriptionBusyTaskId: null, error: '', status: '' };
    const work = { transcriptionBusyTaskId: 'other-task', error: 'other-error', status: 'other-status' };
    const reads = [], paints = [];
    const context = { native: true, state: chat, transcriptionTask: () => ({modelId:'glm-ocr'}),
      invoke: () => pending, tauriBridge: {}, invokeForAuthorizedDataAreaOperation: () => pending,
      loadTranscriptionState: async owner => reads.push(owner), render: () => paints.push(context.state) };
    vm.runInNewContext(handler, context);
    const executing = context.executeTranscriptionTask('task');
    context.state = work;
    settle(rejects ? new Error('synthetic failure') : {state:'COMPLETED'});
    await executing;
    assert.equal(work.status,'other-status');
    assert.equal(work.error,'other-error');
    assert.equal(work.transcriptionBusyTaskId,'other-task');
    assert.equal(chat.transcriptionBusyTaskId,null);
    assert.ok(!paints.includes(work));
    assert.ok(!reads.includes(work));
  });
}
test('authorized OCR retry keeps its original area through the run command', async () => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  const chat = { transcriptionBusyTaskId:null }, work = {};
  const calls = [];
  const command = (name, args, owner) => { calls.push({name,owner:owner || context.state});
    return name === 'retry_desktop_transcription_task' ? pending : Promise.resolve({state:'COMPLETED'}); };
  const context = { native:true,state:chat,transcriptionTask:()=>({modelId:'glm-ocr'}),render(){},loadTranscriptionState:async()=>{},
    invoke:command,tauriBridge:{},invokeForAuthorizedDataAreaOperation:(_bridge,name,args,owner)=>command(name,args,owner) };
  vm.runInNewContext(handler,context);
  const executing = context.executeTranscriptionTask('task',true);
  context.state = work;
  release();
  await executing;
  assert.equal(calls.length,2);
  assert.ok(calls.every(call => call.owner === chat));
});
