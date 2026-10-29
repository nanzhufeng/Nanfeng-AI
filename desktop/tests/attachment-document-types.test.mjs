import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {ATTACHMENT_DOCUMENT_MIME_TYPES} from '../src/attachment-document-types.mjs';
import {autoCandidate} from '../src/auto-chat-routing.mjs';
const fixture=JSON.parse(await readFile(new URL('../../protocol/fixtures/attachment-document-types-v1.json',import.meta.url),'utf8'));
const settings=[{providerId:'DEEPSEEK',enabled:true,credentialStored:true,presets:[{id:'DEEPSEEK_V4_PRO',displayName:'Pro'}]}];
test('source preview MIME declaration matches the shared built fixture',()=>assert.deepEqual(ATTACHMENT_DOCUMENT_MIME_TYPES,fixture.mimeTypes));
test('all local document formats use text-capable Auto models',()=>{
 for(const mime of [...fixture.mimeTypes,'application/zip'])assert.equal(autoCandidate(settings,{mimeTypes:[mime],webSearchEnabled:false})?.id,'DEEPSEEK_V4_PRO',mime);
});
test('unknown archive-like and binary formats cannot become silently readable',()=>{
 for(const mime of ['application/x-rar-compressed','application/octet-stream'])assert.equal(autoCandidate(settings,{mimeTypes:[mime],webSearchEnabled:false}),null);
});
