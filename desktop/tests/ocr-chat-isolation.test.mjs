import test from 'node:test';
import assert from 'node:assert/strict';
import {autoCandidate} from '../src/auto-chat-routing.mjs';

test('an OCR-only enabled credential never becomes an Auto chat recipient across task types',()=>{
 const services=[{providerId:'ZHIPU',enabled:true,credentialStored:true,presets:[{id:'GLM_OCR',chatSelectable:false}]}];
 for(const input of [{text:'合成普通问题'},{text:'深入分析合成问题'},{text:'合成图片',mimeTypes:['image/png']},{text:'合成材料',mimeTypes:['text/plain']}]){
  assert.equal(autoCandidate(services,input),null);
 }
});

test('even a permissive stale OCR entry cannot displace ordinary Zhipu Auto routing',()=>{
 const services=[{providerId:'ZHIPU',enabled:true,credentialStored:true,presets:[{id:'GLM_OCR',chatSelectable:true},{id:'GLM_5_3_FLASH',chatSelectable:true},{id:'GLM_5_3',chatSelectable:true}]}];
 assert.equal(autoCandidate(services,{text:'合成普通问题'}).id,'GLM_5_3_FLASH');
 assert.equal(autoCandidate(services,{text:'深入分析合成问题'}).id,'GLM_5_3');
 assert.equal(autoCandidate(services,{text:'合成材料',mimeTypes:['text/plain']}).id,'GLM_5_3_FLASH');
 assert.equal(autoCandidate(services,{text:'合成图片',mimeTypes:['image/png']}),null);
});
