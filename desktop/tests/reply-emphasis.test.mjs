import test from 'node:test';
import assert from 'node:assert/strict';
import { createReplyEmphasisBudget, isCautionHeading } from '../src/reply-text-emphasis.mjs';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const count=(html,tone)=>(html.match(new RegExp(`class="chat-text-${tone}"`,'g'))||[]).length;
const md=(text,budget=createReplyEmphasisBudget(),options={})=>renderSafeMarkdown(text,{emphasis:budget,...options});
function shell(messages) {
 const data={summary:{id:'w'},exchange:{projects:[],knowledge:[],memory:[],relations:[],conversations:[{id:'c',title:'合成回答',revision:1,messages:messages.map((m,i)=>({id:`m${i}`,createdAt:`2026-10-16T00:00:0${i}Z`,...m}))}]}};
 return renderChatFirstShell({data,native:true,selectedConversationId:'c',pane:'chat',status:'',error:'',connection:{}});
}
const text=value=>({kind:'TEXT',text:value});
test('budget allows two important and one caution within exactly 96 UTF-16 characters',()=>{
 const b=createReplyEmphasisBudget();assert.equal(b.take('甲'.repeat(40)),'important');assert.equal(b.take('乙'.repeat(40)),'important');assert.equal(b.take('注意'+'丙'.repeat(18)),null);assert.equal(b.take('注意'+'丙'.repeat(14)),'caution');assert.equal(b.take('更多提醒'),null);assert.equal(b.take('更多重点'),null);
});
test('invalid/unsafe/numeric candidates do not consume the limited emphasis budget',()=>{
 const b=createReplyEmphasisBudget();for(const value of ['甲','甲'.repeat(41),'1234','99.5%','`代码`','<script>','A&B','NFMARKDOWNTOKEN0X'])assert.equal(b.take(value),null,value);
 assert.equal(b.take('有效重点'),'important');assert.equal(b.take('第二重点'),'important');assert.equal(isCautionHeading('二、**潜在风险**'),true);
});
test('strong spans receive finite semantic classes while all other bold stays bold',()=>{
 const html=md('**第一重点** **第二重点** **第三重点** **注意边界** **风险重复**');assert.equal(count(html,'important'),2);assert.equal(count(html,'caution'),1);assert.match(html,/<strong>第三重点<\/strong>/);assert.match(html,/<strong>风险重复<\/strong>/);
});
test('one assistant reply shares budget across text blocks; each following reply gets a fresh budget',()=>{
 const blocks=[text('**第一重点**'),text('**第二重点** **第三重点**'),text('**注意边界** **警告重复**')];
 const html=shell([{role:'user',blocks:[text('**用户原文**')]},{role:'assistant',blocks},{role:'ASSISTANT',blocks}]);assert.equal(count(html,'important'),4);assert.equal(count(html,'caution'),2);assert.match(html,/\*\*用户原文\*\*/);
});
test('reasoning and inert readers never consume the assistant text budget',()=>{
 const html=shell([{role:'assistant',blocks:[{kind:'REASONING',text:'**思考重点** **注意思考**'},text('**实际重点** **第二重点** **注意边界**')]}]);assert.equal(count(html,'important'),2);assert.equal(count(html,'caution'),1);assert.match(html,/<strong>思考重点<\/strong>/);
 const b=createReplyEmphasisBudget();assert.equal(count(md('**文件重点**',b,{inert:true}),'important'),0);assert.equal(b.take('有效重点'),'important');assert.equal(b.take('第二重点'),'important');
});
test('caution headings carry across blocks and a later ordinary heading resets only section tone',()=>{
 const b=createReplyEmphasisBudget();const a=md('## **注意事项**\n\n**第一个限制**',b),c=md('**第二个限制**\n\n## 建议\n\n**可行方案**',b);assert.equal(count(a,'caution'),1);assert.equal(count(c,'caution'),0);assert.equal(count(c,'important'),1);assert.match(a,/<h3><strong>注意事项<\/strong><\/h3>/);
});
test('code, tables, blockquotes, numbers and tokenized inline code do not steal prose colors',()=>{
 const b=createReplyEmphasisBudget();const excluded=md('```txt\n**代码块**\n```\n\n`**行内代码**` **`代码包裹`**\n\n| **表头** | 值 |\n| --- | --- |\n| **表内** | 1 |\n\n> **引用文字**\n\n**1234**',b);assert.match(excluded,/<blockquote>/);assert.match(excluded,/<table>/);assert.match(excluded,/<pre class="chat-markdown-code"/);assert.equal(count(excluded,'important'),0);assert.equal(count(excluded,'caution'),0);assert.equal(count(md('**正文重点** **第二重点**',b),'important'),2);
});
test('HTML escaping, safe sources and authored Chinese punctuation survive added classes',()=>{
 const html=md('**安全重点**、_中文强调_。\n\n<script data-name="__literal">window.__literal=true</script>\n\n**[危险](javascript:alert(1))** [来源](https://example.test)');assert.equal(count(html,'important'),1);assert.doesNotMatch(html,/<script|href="javascript:|<img/);assert.match(html,/&lt;script data-name=&quot;__literal&quot;&gt;/);assert.match(html,/、<em>中文强调<\/em>。/);assert.match(html,/https:\/\/example.test/);
});
test('search highlighting stays inside colored content without changing its class',()=>{
 const html=md('**关键建议**',createReplyEmphasisBudget(),{query:'关键'});assert.match(html,/<strong class="chat-text-important"><mark>关键<\/mark>建议<\/strong>/);
});
test('re-rendering a streaming reply starts a fresh deterministic budget rather than accumulating it',()=>{
 const render=value=>shell([{role:'assistant',delivery:'PARTIAL',runtimeState:'RUNNING',blocks:[text(value)]}]);assert.equal(count(render('**第一重点**'),'important'),1);assert.equal(count(render('**第一重点** **第二重点** **第三重点**'),'important'),2);assert.equal(render('**第一重点** **第二重点**'),render('**第一重点** **第二重点**'));
});
test('ordinary markdown consumers do not opt into answer emphasis implicitly',()=>{
 assert.equal(count(renderSafeMarkdown('**普通加粗** **注意事项**'),'important'),0);assert.equal(count(renderSafeMarkdown('**普通加粗** **注意事项**'),'caution'),0);
});
