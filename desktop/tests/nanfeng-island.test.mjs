import test from 'node:test';
import assert from 'node:assert/strict';
import { mountNanfengIsland,IslandPresentation,islandMarkup,taskStage,taskDetail} from '../src/nanfeng-island.mjs';
const task=(key='CHAT:a',state='RUNNING',savedCharacters=123)=>({key,state,savedCharacters,startedAt:1000,updatedAt:3000,modelLabel:'6 Astra · RC',dataArea:'CHAT'});
test('task progress uses saved characters and actual state, never estimates completion',()=>{
  assert.equal(taskStage(task('a','RUNNING',0)),'等待模型输出');
  assert.equal(taskDetail(task(),9000),'已保存 123 字 · 8 秒');
  assert.equal(taskDetail(task('a','UNKNOWN'),9000),'已保存 123 字 · 2 秒');
  const c=new IslandPresentation();c.update([task()],3000);c.toggle(3000);
  const html=islandMarkup(c,3000);assert.match(html,/6 Astra · RC/);assert.match(html,/停止/);assert.doesNotMatch(html,/%|tokens|暂停/);
});
test('expanded results pause dismissal and resume a full countdown on collapse',()=>{
  const c=new IslandPresentation();c.update([task('a','COMPLETED')],4000);c.toggle(4500);
  assert.equal(c.visible(60000).length,1);c.toggle(60000);
  assert.equal(c.visible(62000).length,1);assert.equal(c.visible(63000).length,0);
});
test('settings feedback temporarily presents above running tasks without stopping or losing them',()=>{
  const c=new IslandPresentation();c.update([task()],3000);c.feedback('API Key 已安全保存在本机。','success',3000);
  assert.match(islandMarkup(c,3001),/API Key/);assert.match(islandMarkup(c,6000),/正在生成/);
  c.feedback('设置保存失败','error',7000);c.toggle(7000);assert.match(islandMarkup(c,60000),/设置保存失败/);
});
test('multi-task details preserve identities, escape text, and provide only supported actions',()=>{
  const c=new IslandPresentation();c.update([task('CHAT:a'),{...task('WORK:a'),modelLabel:'<script>secret</script>'}],3000);c.toggle(3000);
  const html=islandMarkup(c,3001);assert.match(html,/2 个对话生成中/);assert.match(html,/data-island-key="WORK:a"/);assert.doesNotMatch(html,/<script>/);
});
test('failed and cancelled tasks remain truthful; dismissal leaves active tasks available',()=>{
  const c=new IslandPresentation();c.update([task('a','UNKNOWN'),task('b','CANCELLED'),task('c')],3000);c.toggle(3000);
  assert.match(islandMarkup(c,3000),/生成中断/);assert.match(islandMarkup(c,3000),/已停止生成/);
  c.dismiss();assert.deepEqual(c.visible(3001).map(t=>t.key),['c']);
  assert.equal(taskStage(task('x','COMPLETED_ACCOUNTING_PENDING')),'回复已完成');
});
test('expanding a newer running task does not resurrect expired results',()=>{
  const c=new IslandPresentation();c.update([task('old','COMPLETED')],3000);
  assert.equal(c.visible(7000).length,0);
  c.update([task('old','COMPLETED'),{...task('new'),startedAt:7000,updatedAt:7000}],7000);c.toggle(7000);
  assert.deepEqual(c.visible(7001).map(t=>t.key),['new']);
});
test('outside collapse changes only presentation and removes the redundant collapse button',()=>{
  const c=new IslandPresentation();c.update([task()],3000);c.toggle(3000);
  assert.equal(c.expanded,true);assert.doesNotMatch(islandMarkup(c,3000),/data-island-action="toggle">收起/);
  c.collapse(4000);assert.equal(c.expanded,false);assert.deepEqual(c.visible(4001).map(t=>t.key),['CHAT:a']);assert.equal(c.tasks[0].state,'RUNNING');
  c.collapse(4002);assert.equal(c.expanded,false);
});

test('foreground presentation never reads generation tasks or displays an island', () => {
  const original = globalThis.document;
  globalThis.document = { addEventListener() {}, removeEventListener() {}, activeElement: null };
  const host = { innerHTML: 'old island', addEventListener() {}, contains: () => false, firstElementChild: null };
  let reads = 0;
  const presenter = mountNanfengIsland({ host, invoke: async () => { reads++; return []; } });
  try {
    assert.equal(reads, 0);
    presenter.feedback('saved'); presenter.update([task()]);
    assert.equal(host.innerHTML, '');
  } finally { presenter.dispose(); globalThis.document = original; }
});

test('interrupted generation expires after three seconds even while expanded and preserves running peers', () => {
  const c = new IslandPresentation();
  c.update([task()], 3000); c.toggle(3000);
  c.update([task('CHAT:a', 'UNKNOWN')], 3000);
  assert.equal(c.visible(5900).length, 1);
  assert.equal(c.visible(6100).length, 0);
  assert.equal(c.expanded, false);
  c.update([task('CHAT:a', 'UNKNOWN'), task('CHAT:peer')], 6200);
  assert.deepEqual(c.visible(6200).map(t => t.key), ['CHAT:peer']);
  assert.equal(islandMarkup(c,6200).includes('生成中断'), false);
});

test('a fresh generation starts compact even if the prior expanded task vanished between polls', () => {
  const c = new IslandPresentation();
  c.update([task('old')],3000);
  assert.equal(c.expanded,false);
  c.toggle(3000);
  c.update([{...task('new'),startedAt:9000,updatedAt:9000}],9000);
  assert.equal(c.expanded,false);
  assert.match(islandMarkup(c,9000),/aria-expanded="false"/);
});
