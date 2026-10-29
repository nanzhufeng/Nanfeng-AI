import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {autoCandidate} from '../src/auto-chat-routing.mjs';
import {MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
const cases=JSON.parse(await readFile(new URL('./fixtures/auto-provider-order-cases-v1.json',import.meta.url),'utf8'));
for(const row of cases)for(const [kind,text,mimeTypes] of [['ordinary','本机合成普通问题',[]],['complex','请给出完整方案',[]],['attachment','查看这份合成资料',['text/plain']]])test(`Auto provider order ${row.name}/${kind}`,()=>{
 const settings=MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:row.enabled.includes(s.providerId),credentialStored:true})),before=structuredClone(settings);
 assert.equal(autoCandidate(settings,{text,mimeTypes,webSearchEnabled:false})?.id,row[kind]);assert.deepEqual(settings,before);
});
