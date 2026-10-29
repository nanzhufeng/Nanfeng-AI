import test from 'node:test';
import assert from 'node:assert/strict';
import {renderSafeMarkdown} from '../src/safe-markdown.mjs';
import {createReplyEmphasisBudget} from '../src/reply-text-emphasis.mjs';
const codes=html=>[...html.matchAll(/<code>([\s\S]*?)<\/code>/g)].map(m=>m[1]);

test('an indented continuation stays inside its bullet and code spans soft lines',()=>{
 const html=renderSafeMarkdown('- 开头 `first\n  second` 结束\n- 下一项');
 assert.deepEqual(codes(html),['first second']);assert.match(html,/<li data-depth="0">开头 <code>first second<\/code> 结束<\/li>/);assert.doesNotMatch(html,/<p>/);
});
test('ordered and task items retain their markers and share continuous code protection',()=>{
 const ordered=renderSafeMarkdown('12. ``alpha\n    beta ` gamma``\n13. 下一项');
 assert.deepEqual(codes(ordered),['alpha beta ` gamma']);assert.match(ordered,/<li data-depth="0" value="12">/);assert.match(ordered,/<li data-depth="0" value="13">/);
 const task=renderSafeMarkdown('- [x] `task\n  body`');assert.deepEqual(codes(task),['task body']);assert.match(task,/type="checkbox" disabled checked/);
});
test('lazy plain continuations belong to the same item without crossing its next marker',()=>{
 const html=renderSafeMarkdown('- `lazy\nline`\n- 下一项');assert.deepEqual(codes(html),['lazy line']);assert.equal((html.match(/<li /g)||[]).length,2);assert.doesNotMatch(html,/<p>/);
});
test('structural indentation is removed while internal code spaces survive',()=>{
 assert.deepEqual(codes(renderSafeMarkdown('- `left \n   right`')),['left   right']);
 assert.deepEqual(codes(renderSafeMarkdown('- `tab\n\tend`')),['tab   end']);
});
test('item boundaries and block boundaries never join unrelated code delimiters',()=>{
 for(const separator of ['\n- ','\n\n','\n# 标题\n','\n> '])assert.deepEqual(codes(renderSafeMarkdown('- `open'+separator+'close`')),[],separator);
 assert.deepEqual(codes(renderSafeMarkdown('- `single`\n- `next`')),['single','next']);
});
test('source syntax in continued code stays literal while ordinary continued sources remain active',()=>{
 const code=renderSafeMarkdown('- `first\n  [label]\n  (https://example.test/in-code)\n  end`');assert.deepEqual(codes(code),['first [label] (https://example.test/in-code) end']);assert.doesNotMatch(code,/chat-source-shortcut|href=/);
 const prose=renderSafeMarkdown('- [source]\n(https://example.test/source)');assert.match(prose,/chat-source-shortcut/);assert.doesNotMatch(prose,/<p>/);
});
test('reader and answer use the same continued list text and protect prose color budgets',()=>{
 const text='- `**代码重点**\n  $x$`\n- **正文重点一**\n  **正文重点二**\n  **注意：正文提醒**';
 const answer=renderSafeMarkdown(text,{emphasis:createReplyEmphasisBudget()});assert.deepEqual(codes(answer),['**代码重点** $x$']);assert.doesNotMatch(answer,/chat-markdown-math/);assert.equal((answer.match(/class="chat-text-important"/g)||[]).length,2);assert.equal((answer.match(/class="chat-text-caution"/g)||[]).length,1);
 const reader=renderSafeMarkdown(text,{inert:true});assert.deepEqual(codes(reader),['**代码重点** $x$']);assert.doesNotMatch(reader,/data-action=|chat-text-important|chat-text-caution/);
});
