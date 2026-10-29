import test from 'node:test';
import assert from 'node:assert/strict';
import {renderSafeMarkdown} from '../src/safe-markdown.mjs';
import {createReplyEmphasisBudget} from '../src/reply-text-emphasis.mjs';

test('mixed nested lists are children of the owning item and return to the parent sibling',()=>{
 const html=renderSafeMarkdown('- 父项\n  3. 编号子项\n     - 孙项\n  4. 编号次项\n- 父级次项');
 assert.match(html,/<li data-depth="0">父项<ol[^>]*><li data-depth="1" value="3">编号子项<ul[^>]*><li data-depth="2">孙项<\/li><\/ul><\/li><li data-depth="1" value="4">编号次项<\/li><\/ol><\/li><li data-depth="0">父级次项<\/li>/);
});

test('blank separated paragraphs and fenced code stay inside their list item',()=>{
 const html=renderSafeMarkdown('- 首段\n\n  第二段\n\n  ```txt\n  - 代码标记\n  <script>literal</script>\n  ```\n- 下一项');
 assert.match(html,/<li data-depth="0"><p>首段<\/p><p>第二段<\/p><div class="chat-markdown-copyable chat-markdown-code-copyable/);
 assert.match(html,/<code[^>]*>[\s\S]*代码标记[\s\S]*&lt;script&gt;literal&lt;\/script&gt;[\s\S]*<\/code>[\s\S]*<\/li><li data-depth="0"><p>下一项<\/p><\/li>/);
 assert.equal((html.match(/<li /g)||[]).length,2);assert.doesNotMatch(html,/<script>/);
});

test('a checked task owns nested list blocks in a flow container',()=>{
 const html=renderSafeMarkdown('- [x] 已完成父任务\n  - [ ] 子任务\n    9. 子步骤\n- 普通同级');
 assert.match(html,/<li class="chat-markdown-task" data-depth="0"><input[^>]*checked[^>]*><div class="chat-markdown-task-content">已完成父任务<ul/);
 assert.match(html,/<li class="chat-markdown-task" data-depth="1">/);assert.match(html,/<li data-depth="2" value="9">子步骤<\/li>/);
 assert.doesNotMatch(html,/<span>[^<]*<ul/);
});

test('nested continuation code keeps content indentation and never spans a sibling',()=>{
 const html=renderSafeMarkdown('- 父项\n  - `alpha\n    beta`\n  - `unclosed\n  - next`\n- 末项');
 assert.match(html,/<li data-depth="1"><code>alpha beta<\/code><\/li>/);
 assert.equal((html.match(/<code>/g)||[]).length,1);assert.equal((html.match(/<li /g)||[]).length,5);
});

test('nested blocks share the reply color budget while reader mode remains inert',()=>{
 const text='- **重点甲**\n  - **重点乙**\n    - **重点丙**\n  - **注意边界**\n\n  ```txt\n  **代码重点**\n  ```';
 const answer=renderSafeMarkdown(text,{emphasis:createReplyEmphasisBudget()});
 assert.equal((answer.match(/class="chat-text-important"/g)||[]).length,2);assert.equal((answer.match(/class="chat-text-caution"/g)||[]).length,1);
 assert.match(answer,/<pre[^>]*>[\s\S]*\*\*代码重点\*\*[\s\S]*<\/pre>/);
 const reader=renderSafeMarkdown(text,{inert:true});assert.doesNotMatch(reader,/data-action=|chat-text-important|chat-text-caution/);assert.match(reader,/<li data-depth="2">/);
});

test('outdented prose and a different root marker do not get swallowed by an item',()=>{
 const html=renderSafeMarkdown('- 父项\n  2. 子项\n\n独立正文\n\n7. 根编号');
 assert.match(html,/<\/ol><\/li><\/ul><p>独立正文<\/p><ol[^>]*><li data-depth="0" value="7">根编号<\/li><\/ol>/);
});

test('extreme list depth preserves text without overflowing the JavaScript stack',()=>{
 const text=Array.from({length:1000},(_,i)=>'  '.repeat(i)+'- 节点'+String(i).padStart(4,'0')).join('\n');
 let html;assert.doesNotThrow(()=>{html=renderSafeMarkdown(text);});
 for(const n of ['0000','0063','0064','0999'])assert.equal((html.match(new RegExp('节点'+n,'g'))||[]).length,1);
});
