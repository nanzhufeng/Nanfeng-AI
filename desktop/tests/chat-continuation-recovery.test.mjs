import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
function render(code, text='') {
 const conversation={id:'conversation',title:'恢复',revision:1,messages:[{id:'saved',role:'assistant',delivery:'COMPLETE',blocks:[{kind:'TEXT',text:'已保存的断点文字'}]},{id:'reply',role:'assistant',delivery:'FAILED',runtimeState:'TERMINAL',runtimeSafeErrorCode:code,attemptId:'attempt',continuationOf:'saved',blocks:[{kind:'TEXT',text}]}]};
 return renderChatFirstShell({data:{summary:{id:'workspace'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[]}},native:true,selectedConversationId:'conversation',composerDraft:'',chatSearch:'',profileOpen:false,sidebarOpen:false,railCollapsed:false,pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'}});
}
test('echo-only child retains the saved checkpoint recovery action',()=>{
 assert.match(render('RESPONSE_INCOMPLETE'),/从断点继续/);
});
test('incomplete search scope regenerates material instead of continuing provisional text',()=>{
 const html=render('WEB_SEARCH_SCOPE_INCOMPLETE','有限范围的回答');
 assert.match(html,/联网搜索未完成/);assert.match(html,/重新生成/);assert.doesNotMatch(html,/从断点继续/);
});
