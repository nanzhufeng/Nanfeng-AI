import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { createReplyEmphasisBudget } from '../src/reply-text-emphasis.mjs';
const unescape = html => html.replace(/&(?:amp|lt|gt|quot|#039);/g, value => ({'&amp;':'&','&lt;':'<','&gt;':'>','&quot;':'"','&#039;':"'"})[value]);
const codes = html => [...html.matchAll(/<code>([\s\S]*?)<\/code>/g)].map(match=>unescape(match[1]));

for(const [name,text,expected] of [
 ['dollar math','`$x$`',['$x$']],
 ['escaped math','`\\(x^2\\)`',['\\(x^2\\)']],
 ['private citation','`citeturn0search0`',['citeturn0search0']],
 ['different backtick runs','``a ` b`` and ```c `` d```',['a ` b','c `` d']],
 ['edge spaces','`` a ` b `` and `   `',['a ` b','   ']],
 ['literal html and links','`<script>x</script> [link](https://example.test) **重点**`',['<script>x</script> [link](https://example.test) **重点**']],
 ['authored token collision','`NFMARKDOWNTOKEN1X` then `second`',['NFMARKDOWNTOKEN1X','second']],
])test(`inline code preserves ${name}`,()=>{
 const html=renderSafeMarkdown(text,{emphasis:createReplyEmphasisBudget()});assert.deepEqual(codes(html),expected);
 assert.doesNotMatch(html,/chat-markdown-math|chat-source-shortcut|chat-text-important|chat-text-caution|<script>/);
});

test('ordinary math and link remain live after a literal code span; authored token text stays authored',()=>{
 const html=renderSafeMarkdown('`$literal$` $actual$ NFMARKDOWNTOKEN0X [source](https://example.test/prose)');
 assert.deepEqual(codes(html),['$literal$']);assert.match(html,/aria-label="actual"/);assert.match(html,/chat-source-shortcut/);
 assert.ok(html.includes('NFMARKDOWNTOKEN0X'));
});

test('code in a link label and image alt remains readable without leaking internal tokens',()=>{
 const html=renderSafeMarkdown('[`$label$`](https://example.test/label) ![`<alt>`](https://example.test/image)');
 assert.doesNotMatch(html,/NFMARKDOWNTOKEN|<alt>|<img|<script>/);assert.match(html,/\$label\$/);assert.match(html,/&lt;alt&gt;/);
});

test('code spans never consume prose colors, and inert readers have no source controls',()=>{
 const text='``**代码 ` 重点**`` `$x$` **正文重点一** **正文重点二** **注意边界**';
 const html=renderSafeMarkdown(text,{emphasis:createReplyEmphasisBudget()});
 assert.deepEqual(codes(html),['**代码 ` 重点**','$x$']);
 assert.equal((html.match(/class="chat-text-important"/g)||[]).length,2);assert.equal((html.match(/class="chat-text-caution"/g)||[]).length,1);
 const inert=renderSafeMarkdown(text,{inert:true,emphasis:createReplyEmphasisBudget()});assert.deepEqual(codes(inert),['**代码 ` 重点**','$x$']);assert.doesNotMatch(inert,/data-action=|chat-text-important|chat-text-caution/);
});

test('a protected code span inside dollar delimiters keeps priority over formula projection',()=>{
 const html=renderSafeMarkdown('$before `literal` after$');assert.deepEqual(codes(html),['literal']);
 assert.doesNotMatch(html,/NFMARKDOWNTOKEN|chat-markdown-math/);assert.match(html,/\$before/);
});

test('escaped literal HTML preserves authored backticks inside attributes',()=>{
 const html=renderSafeMarkdown('<span title="`literal`">raw</span>');
 assert.deepEqual(codes(html),[]);assert.match(html,/&lt;span title=&quot;`literal`&quot;&gt;raw&lt;\/span&gt;/);
 assert.doesNotMatch(html,/NFMARKDOWNTOKEN|<span/);
});

test('code delimiters inside a link destination do not rewrite the destination to an internal token',()=>{
 const html=renderSafeMarkdown('[source](https://example.test/`literal`)');
 assert.doesNotMatch(html,/NFMARKDOWNTOKEN/);assert.match(html,/https:\/\/example.test\/%60literal%60/);
});
