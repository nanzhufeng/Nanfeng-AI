import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {renderChatFirstShell} from '../src/chat-shell.mjs';
const cases=JSON.parse(await readFile(new URL('./fixtures/auto-composer-text-cases-v1.json',import.meta.url),'utf8'));
const settings=[{providerId:'RELAYCORE_GPT',enabled:true,credentialStored:true,presets:[{id:'RELAYCORE_GPT_6_1_SOL',displayName:'GPT-6.1 Sol'},{id:'RELAYCORE_GPT_6_ASTRA',displayName:'GPT-6 Astra'}]}];
for(const row of cases)test(`composer Auto predicts the same submitted-text route: ${row.name}`,()=>{
 const text=(row.prefix||'')+(row.text??row.glyph.repeat(row.count))+(row.suffix||'');
 const options={data:{summary:{id:'fixture-normalized'},exchange:{conversations:[]}},native:true,pane:'chat',selectedConversationId:null,composerDraft:text,modelServiceSettings:settings,p6gModelPickerOpen:true};
 const before=structuredClone(options),html=renderChatFirstShell(options),label=html.match(/aria-label="选择模型：([^"]*)"/)?.[1];
 assert.ok(label?.includes(row.preset.endsWith('ASTRA')?'Astra':'Sol'),`${row.name}: ${label}`);assert.deepEqual(options,before);
});
