import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve,join,sep} from 'node:path';
import {preflightPackage} from '../../protocol/scripts/exchange-lib.mjs';
const root=resolve(process.argv[2]);
const marker=JSON.parse(await readFile(join(root,'.nanfeng-windows-acceptance.json'),'utf8'));
assert.equal(marker.identifier,'com.nanzhufeng.ai.desktop.windowsacceptance');
const evidence=JSON.parse(await readFile(join(root,'history-evidence.json'),'utf8'));
assert.equal(evidence.pass,true);assert.ok(!evidence.forcedCleanup);
const results=[];
for(const lib of evidence.libraries){
 const zip=Boolean(lib.provider);
 const inputPath=zip?lib.paths.at(-1)+'.json':lib.paths[0];
 assert.ok(resolve(inputPath).startsWith(root+sep));
 const input=JSON.parse(await readFile(inputPath,'utf8'));
 const exportPath=resolve(root,zip?'synthetic-history-zip':'synthetic-history-json',`${lib.area}-${lib.format.name}-complete.nfai-exchange`);
 const packageResult=preflightPackage(await readFile(exportPath));
 const rows=packageResult.exchange.conversations.filter(x=>x.id!==lib.seedConversationId);
 assert.equal(rows.length,input.length);
 for(const item of input){
  const matches=rows.filter(x=>x.title===(item.title??item.name));assert.equal(matches.length,1);
  const texts=item.mapping?Object.values(item.mapping).map(n=>n.message.content.parts[0]):(item.chat_messages??JSON.parse(item.sourceText).chat_messages).map(x=>x.text);
  assert.deepEqual(matches[0].messages.map(m=>m.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n')),texts);
 }
 results.push({area:lib.area,format:lib.format.name,conversations:rows.length,entryCount:packageResult.entryCount,exactBodies:true});
}
console.log(JSON.stringify({allOk:true,results}));
