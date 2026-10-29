import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { createReplyEmphasisBudget } from '../src/reply-text-emphasis.mjs';

const unescape = html => html.replace(/&(?:amp|lt|gt|quot|#039);/g, value => ({'&amp;':'&','&lt;':'<','&gt;':'>','&quot;':'"','&#039;':"'"})[value]);
const codes = html => [...html.matchAll(/<pre class="chat-markdown-code"[^>]*><code>([\s\S]*?)<\/code><\/pre>/g)].map(match=>unescape(match[1].replace(/<[^>]*>/g,'')));
const count = (html,tone) => (html.match(new RegExp(`class="chat-text-${tone}"`,'g'))||[]).length;

for (const [name,open,close,body] of [
 ['long backticks','````md','`````','**代码重点**\n```\n## 注意事项\n[代码来源]\n(https://example.test/code)\n~~~'],
 ['tilde fence','~~~txt','~~~~','**代码重点**\n```\n## 注意事项\n[代码来源]\n(https://example.test/code)'],
 ['short/mismatched closes','~~~~text','~~~~','首行\n~~~\n```\n**代码重点**\n尾行'],
]) test(`${name} preserves literal code and reserves emphasis for prose`,()=>{
 const budget=createReplyEmphasisBudget();
 const html=renderSafeMarkdown([open,body,close,'','**正文重点一** **正文重点二** **注意边界**'].join('\n'),{emphasis:budget});
 assert.deepEqual(codes(html),[body]);
 assert.equal(count(html,'important'),2);assert.equal(count(html,'caution'),1);
 assert.doesNotMatch(html,/<h\d|chat-source-shortcut/);
 const copy=[...html.matchAll(/data-copy-text="([^"]*)"/g)].map(match=>unescape(match[1]));
 assert.deepEqual(copy,[body]);
});

test('a longer closing fence closes immediately and restores normal source-link handling',()=>{
 const html=renderSafeMarkdown('```text\n原始代码\n````\n**正文重点**\n\n[正文来源]\n(https://example.test/prose)',{emphasis:createReplyEmphasisBudget()});
 assert.deepEqual(codes(html),['原始代码']);assert.equal(count(html,'important'),1);
 assert.match(html,/chat-source-shortcut/);assert.match(html,/https:\/\/example.test\/prose/);
});

test('unclosed long/tilde fences remain literal to EOF, including potential HTML and bold',()=>{
 for(const open of ['````html','~~~html']){
  const body='<script>window.__fenceExecuted=true</script>\n**不是正文重点**\n[来源]\n(https://example.test/code)';
  const html=renderSafeMarkdown(open+'\n'+body,{emphasis:createReplyEmphasisBudget()});
  assert.deepEqual(codes(html),[body]);assert.equal(count(html,'important'),0);
  assert.doesNotMatch(html,/<script|chat-source-shortcut/);
 }
});

test('fence info uses the first language token and inert readers keep exact code without controls',()=>{
 const body='原始\t缩进\n  **代码不是重点**';
 const html=renderSafeMarkdown('~~~text title=synthetic\n'+body+'\n~~~',{inert:true,emphasis:createReplyEmphasisBudget()});
 assert.deepEqual(codes(html),[body]);assert.match(html,/data-language="text"/);
 assert.doesNotMatch(html,/data-action=|chat-text-important|chat-text-caution/);
});

test('a bare legacy backtick fence still renders ordinary code and code search remains escaped',()=>{
 const html=renderSafeMarkdown('```\n<script>关键</script>\n```',{query:'关键'});
 assert.deepEqual(codes(html),['<script>关键</script>']);
 assert.match(html,/<mark>关键<\/mark>/);assert.doesNotMatch(html,/<script>/);
});
