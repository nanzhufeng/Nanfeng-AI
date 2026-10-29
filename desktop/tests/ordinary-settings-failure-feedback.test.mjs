import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

function renderFailure({measured=false,state='FAILED',code='MODEL_SETTINGS_CHANGED'}={}) {
 const conversation={id:'synthetic-feedback-conversation',title:'合成协作失败',revision:3,currentLeafId:'answer',messages:[
  {id:'question',role:'user',delivery:'COMPLETE',blocks:[{kind:'TEXT',text:'合成问题'}]},
  {id:'answer',parentId:'question',role:'assistant',delivery:state,runtimeState:state,safeErrorCode:code,attemptId:'synthetic-feedback-attempt',blocks:[{kind:'TEXT',text:''}]},
 ]};
 const usageRecords=measured?[{entryId:'retrieval-final',executionId:'synthetic-feedback-attempt',conversationId:conversation.id,invocationId:'retrieval',kind:'FINAL_MEASURED',inputTokens:5,outputTokens:2,chargeMicros:4,currencyCode:'USD',costSource:'PROVIDER_REPORTED',callMetadata:{role:'RETRIEVAL',modelDisplayName:'GPT-6 Luna · OpenRouter'}}]:[];
 return renderChatFirstShell({data:{summary:{id:'synthetic-workspace'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[]}},selectedConversationId:conversation.id,native:true,pane:'chat',composerDraft:'',chatSearch:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'},usageRecords});
}

for(const measured of [false,true]) test(`settings change feedback does not invent a no-dispatch guarantee, measured retrieval=${measured}`,()=>{
 const html=renderFailure({measured});
 assert.ok(!html.includes('未向服务商发送'),'retrieval can already have reached its disclosed recipient');
 assert.ok(html.includes('请核对模型与调用记录后手动重试'));
 assert.ok(html.includes('data-action="retry-ordinary-chat"'));
 assert.ok(html.includes('data-attempt-id="synthetic-feedback-attempt"'));
});

test('unknown retrieval keeps a distinct uncertain-result explanation and explicit retry',()=>{
 const html=renderFailure({measured:true,state:'UNKNOWN',code:'NETWORK'});
 assert.ok(html.includes('未知')||html.includes('未确认'));
 assert.ok(html.includes('data-action="retry-ordinary-chat"'));
 assert.ok(!html.includes('未向服务商发送'));
});

for(const measured of [false,true]) test(`explicit provider refusal keeps actionable copy and never claims no dispatch, measured=${measured}`,()=>{
 const html=renderFailure({measured,code:'PROVIDER_REFUSAL'});
 assert.ok(html.includes('服务商明确拒绝生成本次内容，已停止后续请求'));
 assert.ok(html.includes('请核对调用记录后手动调整问题'));
 assert.ok(html.includes('data-action="retry-ordinary-chat"'));
 assert.ok(!html.includes('未向服务商发送'));
 assert.ok(!html.includes('>PROVIDER_REFUSAL<'));
});

for(const [code,copy] of [['PROVIDER_RESPONSE_FAILED','明确报告本次响应失败'],['INCOMPLETE_RESPONSE','明确报告响应未完成'],['OUTPUT_LIMIT','达到输出上限']]) {
 for(const measured of [false,true]) test(`Responses terminal ${code} keeps distinct Chinese recovery feedback, measured=${measured}`,()=>{
  const html=renderFailure({measured,code});assert.ok(html.includes(copy));
  assert.ok(html.includes('data-action="retry-ordinary-chat"'));assert.ok(!html.includes(`>${code}<`));
  assert.ok(!html.includes('未向服务商发送'));
  if(code==='OUTPUT_LIMIT')assert.ok(!html.includes('只保留已生成内容'),'an empty failed answer cannot promise that provisional output was saved');
 });
}
