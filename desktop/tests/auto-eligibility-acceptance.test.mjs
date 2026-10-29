import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {autoCandidate} from '../src/auto-chat-routing.mjs';
import {MODEL_SERVICE_PREVIEW_SETTINGS} from '../src/android-settings-shell.mjs';
const cases=JSON.parse(await readFile(new URL('./fixtures/auto-eligibility-cases-v1.json',import.meta.url),'utf8'));
for(const row of cases)test('Auto eligibility '+row.name,()=>{
 const settings=MODEL_SERVICE_PREVIEW_SETTINGS.map(s=>({...s,enabled:row.enabled.includes(s.providerId),credentialStored:row.stored.includes(s.providerId),credentialStatus:row.stored.includes(s.providerId)?'STORED':'MISSING'})),before=structuredClone(settings);
 assert.equal(autoCandidate(settings,{text:'本机合成能力交集问题',mimeTypes:row.mimes,webSearchEnabled:row.web})?.id??null,row.expected);assert.deepEqual(settings,before);
});
