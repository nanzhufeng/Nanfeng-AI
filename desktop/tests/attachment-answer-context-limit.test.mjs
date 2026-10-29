import test from 'node:test';
import assert from 'node:assert/strict';
import {renderChatFirstShell} from '../src/chat-shell.mjs';

test('post-search context limit explains retained material and incurred retrieval before sending another request',()=>{
 const conversation={id:'conversation',title:'材料预算',revision:1,messages:[
  {id:'original',role:'user',delivery:'COMPLETE',blocks:[{kind:'TEXT',text:'合成原材料起点。完整正文保留。合成原材料终点。'}]},
  {id:'reply',role:'assistant',delivery:'FAILED',runtimeState:'TERMINAL',runtimeSafeErrorCode:'ANSWER_CONTEXT_LIMIT',attemptId:'attempt',attachmentSearchCoverage:{planned:20,covered:20,limited:false},blocks:[]}
 ]};
 const html=renderChatFirstShell({data:{summary:{id:'workspace'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[]}},native:true,selectedConversationId:'conversation',composerDraft:'',chatSearch:'',profileOpen:false,sidebarOpen:false,railCollapsed:false,pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'}});
 assert.match(html,/上下文容量/);
 assert.match(html,/最终回答未发起/);
 assert.match(html,/检索可能已产生费用/);
 assert.match(html,/查看调用记录/);
 assert.match(html,/拆分材料后重新发送/);
 assert.match(html,/合成原材料起点。完整正文保留。合成原材料终点。/);
 assert.match(html,/已返回检索结果：20 \/ 20 项/);
 assert.doesNotMatch(html,/已核实20|检查模型与网络设置/);
});
