import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { createReplyEmphasisBudget } from '../src/reply-text-emphasis.mjs';
const unescape=html=>html.replace(/&(?:amp|lt|gt|quot|#039);/g,value=>({'&amp;':'&','&lt;':'<','&gt;':'>','&quot;':'"','&#039;':"'"})[value]);
const codes=html=>[...html.matchAll(/<code>([\s\S]*?)<\/code>/g)].map(match=>unescape(match[1]));

test('a paragraph code span crosses a soft line break using equal backtick runs',()=>{
 const html=renderSafeMarkdown('前文 ``first\nsecond ` third`` 后文');
 assert.deepEqual(codes(html),['first second ` third']);assert.match(html,/前文 <code>/);assert.match(html,/<\/code> 后文/);
});

test('spaces adjacent to a code line break survive prose trimming',()=>{
 assert.deepEqual(codes(renderSafeMarkdown('`left \n right`')),['left   right']);
 assert.deepEqual(codes(renderSafeMarkdown('``\nline\n``')),['line']);
});

test('split-link syntax inside a multiline code span stays literal before normalization',()=>{
 const html=renderSafeMarkdown('CODE `start\n[label]\n(https://example.test/inside)\nend`');
 assert.deepEqual(codes(html),['start [label] (https://example.test/inside) end']);
 assert.doesNotMatch(html,/chat-source-shortcut|href=/);
});

test('continuous quoted lines share code protection without consuming prose colors',()=>{
 const html=renderSafeMarkdown('> 引用 `quoted\n> line` 结束\n\n**正文重点一** **正文重点二**',{emphasis:createReplyEmphasisBudget()});
 assert.deepEqual(codes(html),['quoted line']);assert.match(html,/<blockquote>/);
 assert.equal((html.match(/class="chat-text-important"/g)||[]).length,2);
});

test('a blank quoted line remains a paragraph boundary for an unfinished code span',()=>{
 const html=renderSafeMarkdown('> before `open\n>\n> close` after');
 assert.deepEqual(codes(html),[]);assert.match(html,/<blockquote>before `open<br><br>close` after<\/blockquote>/);
});

test('paragraph breaks and headings remain block boundaries for unmatched code',()=>{
 for(const separator of ['\n\n','\n# 标题\n']){
  const html=renderSafeMarkdown('before `open'+separator+'close` after');
  assert.deepEqual(codes(html),[]);
 }
});

test('ordinary soft breaks and separate source controls retain their established shape',()=>{
 assert.equal(renderSafeMarkdown('首行  \n  第二行'),'<p>首行<br>第二行</p>');
 const html=renderSafeMarkdown('[one](https://one.test)\n[two](https://two.test)');
 assert.equal((html.match(/data-action="open-source-links"/g)||[]).length,2);
});

test('split destinations still work in source sections, lists, and ordinary prose',()=>{
 for(const text of ['来源：\n- [source]\n(https://example.test/source)','- [source]\n(https://example.test/source)','前文\n[source]\n(https://example.test/source)']){
  const html=renderSafeMarkdown(text);assert.match(html,/chat-source-shortcut/);assert.match(html,/https:\/\/example.test\/source/);assert.doesNotMatch(html,/>\(https:/);
 }
});

test('multiline code protects formulas, private markers, HTML and authored token names in readers',()=>{
 const text='`$x$\nciteturn0search0\n<script>literal</script> NFMARKDOWNTOKEN0X`';
 const html=renderSafeMarkdown(text,{inert:true,emphasis:createReplyEmphasisBudget()});
 assert.deepEqual(codes(html),['$x$ citeturn0search0 <script>literal</script> NFMARKDOWNTOKEN0X']);
 assert.doesNotMatch(html,/chat-markdown-math|chat-text-important|data-action=|<script>/);
});

test('literal HTML attributes keep authored backticks and ordinary soft breaks',()=>{
 const html=renderSafeMarkdown('<span title="`a\nb`">raw</span>');
 assert.deepEqual(codes(html),[]);
 assert.equal(html,'<p>&lt;span title=&quot;`a<br>b`&quot;&gt;raw&lt;/span&gt;</p>');
});
