import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { RELAYCORE_MODELS, relayCoreModel, relayCoreFooterName } from '../src/relaycore-models.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8').replaceAll('\r\n','\n');
const fn=name=>{const a=source.indexOf(`function ${name}(`);const z=source.indexOf('\nfunction ',a+1);return source.slice(a,z);};
function fixture({explicit=false,empty=false,stale=false,companion=null,answerModel=null}={}){
 const rc=answerModel?relayCoreModel(answerModel):null;
 const owner={dataArea:'CHAT',current:{summary:{id:'w'}},selectedConversationId:explicit?null:'c',composerDraft:empty?'':'合成预览正文',composerAttachments:[],modelServiceSettings:[{providerId:'OPENROUTER',providerDisplayName:'合成服务',enabled:true,credentialStored:true,revision:1}],conversationPreferences:{},p6gGlobalDefault:{},p6gSelection:{},openrouterRegistryStatus:{}};
 if(rc)owner.modelServiceSettings.push({providerId:rc.providerId,providerDisplayName:'RC合成'});
 const saved=new WeakMap(),span={textContent:'保留选择'},trigger={title:'',querySelector:()=>span,setAttribute(){}},line={dataset:{},textContent:'',removed:false,remove(){this.removed=true;}},send={disabled:false};
 const controls={querySelector:s=>s==='.chat-composer-route'?line:s.includes('toggle-p6g')?trigger:s.includes('save-local-message')?send:null};
 const context=vm.createContext({native:true,state:owner,ordinaryEgressPreviews:saved,document:{querySelector:()=>controls},resolveConversation:(_data,id)=>id?{id,revision:1}:null,newConversationModelId:()=>explicit?'SYNTHETIC_OVERRIDE':null,RELAYCORE_MODELS,relayCoreModel,relayCoreFooterName});
 vm.runInContext(fn('ordinaryEgressPreviewInput')+'\n'+fn('updateOrdinaryEgressDisclosure'),context);const route=context.ordinaryEgressPreviewInput(owner);
 if(route)saved.set(owner,{signature:stale?'stale':route.signature,status:'ready',preview:{recipientChoiceId:answerModel||'SYNTHETIC',recipientProviderId:rc?.providerId||'OPENROUTER',recipientModelId:rc?.modelId||'synthetic-model',recipientDisplayName:rc?.modelName||'完整合成接收方',retrievalRecipient:companion}});
 return {context,owner,route,span,line,send};
}
test('ready recipient projection consumes the actual input shape without throwing during render',()=>{
 const f=fixture();assert.deepEqual(Object.keys(f.route).sort(),['input','signature']);assert.equal(f.route.input.modelId,null);assert.doesNotThrow(()=>f.context.updateOrdinaryEgressDisclosure());assert.equal(f.span.textContent,'完整合成接收方');assert.equal(f.line.textContent,'合成服务 · 完整合成接收方');assert.equal(f.line.dataset.routeReady,'true');
});
test('explicit new-chat choice is not overwritten by recipient projection',()=>{
 const f=fixture({explicit:true});assert.equal(f.route.input.modelId,'SYNTHETIC_OVERRIDE');assert.doesNotThrow(()=>f.context.updateOrdinaryEgressDisclosure());assert.equal(f.span.textContent,'保留选择');assert.equal(f.line.dataset.routeReady,'true');
});
test('a stale ready snapshot still disables sending and never claims a ready recipient',()=>{
 const f=fixture({stale:true});f.context.updateOrdinaryEgressDisclosure();assert.equal(f.line.dataset.routeReady,'false');assert.equal(f.send.disabled,true);assert.equal(f.span.textContent,'保留选择');
});
test('an earlier area owner cannot project its disclosure into the current area',()=>{
 const f=fixture();f.context.state={dataArea:'WORK'};f.context.updateOrdinaryEgressDisclosure(f.owner);assert.equal(f.span.textContent,'保留选择');assert.equal(f.line.textContent,'');
});
test('empty composer removes its old disclosure rather than fabricating a new recipient',()=>{
 const f=fixture({empty:true});assert.equal(f.route,null);f.context.updateOrdinaryEgressDisclosure();assert.equal(f.line.removed,true);
});

for(const model of ['RELAYCORE_OPUS_5_5','RELAYCORE_SONNET_5_5','RELAYCORE_GEMINI_3_8_FLASH','RELAYCORE_GEMINI_3_1_PRO']) test(`ready companion disclosure includes retrieval and answer recipient for ${model}`,()=>{
 const grant={disclosureVersion:'rc-luna-retrieval-v1',providerId:'OPENROUTER',presetId:'GPT_6_LUNA',modelId:'openai/gpt-6-luna',materialFingerprint:'synthetic-private-fingerprint'};
 const f=fixture({companion:grant,answerModel:model});f.context.updateOrdinaryEgressDisclosure();
 assert.ok(f.line.textContent.includes('检索：合成服务 · GPT-6 Luna'));
 assert.ok(f.line.textContent.includes(`回答：RC合成 · ${relayCoreModel(model).modelName}`));
 assert.ok(!f.line.textContent.includes(grant.materialFingerprint));
 assert.equal(f.line.dataset.routeReady,'true');
});
test('a stale companion grant is never disclosed as the current recipient',()=>{
 const f=fixture({stale:true,companion:{providerId:'OPENROUTER',presetId:'GPT_6_LUNA',modelId:'openai/gpt-6-luna'}});f.context.updateOrdinaryEgressDisclosure();
 assert.equal(f.line.dataset.routeReady,'false');assert.ok(!f.line.textContent.includes('GPT-6 Luna'));assert.equal(f.send.disabled,true);
});
