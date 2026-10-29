import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

function render(coverage, delivery='PARTIAL') {
  const conversation={id:'conversation',title:'附件核验',revision:1,messages:[{id:'reply',role:'assistant',delivery,runtimeState:'TERMINAL',attemptId:'attempt',attachmentSearchCoverage:coverage,blocks:[{kind:'TEXT',text:'已保留内容'}]}]};
  return renderChatFirstShell({data:{summary:{id:'workspace'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[]}},native:true,selectedConversationId:'conversation',composerDraft:'',chatSearch:'',profileOpen:false,sidebarOpen:false,railCollapsed:false,pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'}});
}
test('partial retrieval shows durable result coverage without claiming verification',()=>{
  const html=render({planned:8,covered:4,limited:false});
  assert.match(html,/已返回检索结果：4 \/ 8 项/);
  assert.match(html,/尚有 4 项缺少可用结果/);
  assert.doesNotMatch(html,/已核实 4/);
  assert.match(html,/未自动重发/);
});
test('completed answer retains coverage and discloses limited material scope',()=>{
  const html=render({planned:8,covered:8,limited:true},'COMPLETE');
  assert.match(html,/已返回检索结果：8 \/ 8 项/);
  assert.match(html,/材料事项超出本轮范围/);
  assert.doesNotMatch(html,/全部核实/);
});
test('invalid or absent coverage cannot fabricate a progress status',()=>{
  for(const coverage of [null,{planned:8,covered:9},{planned:0,covered:0},{planned:8,covered:'<script>'},{planned:8.5,covered:4}]) {
    assert.doesNotMatch(render(coverage),/已返回检索结果/);
  }
});

test('zero planned items retain a limited-scope warning without claiming returned results',()=>{
  for(const delivery of ['COMPLETE','PARTIAL','FAILED']) {
    const html=render({planned:0,covered:0,limited:true},delivery);
    assert.match(html,/材料范围受限/);
    assert.match(html,/尚无可用查询事项/);
    assert.doesNotMatch(html,/已返回检索结果|0 \/ 0 项|全部核实/);
  }
  for(const coverage of [{planned:0,covered:0,limited:false},{planned:0,covered:0,limited:'true'},{planned:0,covered:1,limited:true},{planned:0,covered:'0',limited:true}]) {
    assert.doesNotMatch(render(coverage),/data-attachment-search-coverage/);
  }
});
