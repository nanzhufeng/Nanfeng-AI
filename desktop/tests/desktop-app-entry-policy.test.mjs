import test from 'node:test';
import assert from 'node:assert/strict';
import {APP_ENTRY_RETENTION_MS, DesktopAppEntry, retainedEntry} from '../src/desktop-app-entry.mjs';
const start=1_800_000_000_000;
const record={workspaceId:'workspace',conversationId:'last-exit',backgroundAt:start};
test('short exit and 15 minute boundary match Android entry policy',()=>{
  assert.deepEqual(retainedEntry(record,start+899999),{workspaceId:'workspace',conversationId:'last-exit'});
  assert.equal(retainedEntry(record,start+APP_ENTRY_RETENTION_MS),null);
  assert.equal(retainedEntry(record,start-1),null);
  assert.equal(retainedEntry({...record,conversationId:null},start),null);
  assert.equal(retainedEntry({...record,backgroundAt:0},start),null);
  assert.equal(retainedEntry({...record,hadRunningGeneration:true},start+3600000).conversationId,'last-exit');
});
test('new process restores metadata per area without draft or conversation bodies',()=>{
  let now=start;
  const records=new Map(); const storage={getItem:k=>records.get(k),setItem:(k,v)=>records.set(k,v)};
  const first=new DesktopAppEntry(storage,()=>now);
  first.leave('CHAT','workspace','last-exit');
  now+=1000;first.leave('CHAT','different','pinned');
  const restarted=new DesktopAppEntry(storage,()=>now);
  assert.equal(restarted.read('CHAT').conversationId,'last-exit');
  assert.equal(restarted.read('WORK'),null);
  assert.deepEqual(Object.keys(JSON.parse([...records.values()][0])),['workspaceId','conversationId','backgroundAt','hadRunningGeneration']);
});
test('foreground expiry is decided synchronously and once',()=>{
  let now=start;
  const entry=new DesktopAppEntry({getItem:()=>null,setItem:()=>{}},()=>now);
  entry.leave('CHAT','workspace','last-exit');now+=100;
  assert.equal(entry.enter(),false);
  entry.leave('CHAT','workspace','last-exit');now+=APP_ENTRY_RETENTION_MS;
  assert.equal(entry.enter(),true);
  assert.equal(entry.enter(),false);
});

test('shared Desktop and Android entry cases',async()=>{
  const {readFile}=await import('node:fs/promises');
  const rows=(await readFile(new URL('../../protocol/fixtures/conversation-entry-policy-v1.tsv',import.meta.url),'utf8')).trim().split('\n').slice(1);
  for(const row of rows) {
    const [name,id,backgroundAt,now,running,expected]=row.split('\t');
    const actual=retainedEntry({workspaceId:'workspace',conversationId:id==='-'?null:id,backgroundAt:Number(backgroundAt),hadRunningGeneration:running==='true'},Number(now));
    assert.equal(actual?.conversationId ?? null,expected==='-'?null:expected,name);
  }
});
