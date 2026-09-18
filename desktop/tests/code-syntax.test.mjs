import test from 'node:test';
import assert from 'node:assert/strict';
import {codeSyntaxSpans} from '../src/code-syntax.mjs';
import {renderSafeMarkdown} from '../src/safe-markdown.mjs';

test('TOML source tokens retain exact offsets and distinguish comments, strings, keys and sections', () => {
  const code = '# 注释\nmodel = "deepseek-v4-flash"\n[model_providers.deepseek]\nurl = "https://example.test/#frag"\ncount = 42\nenabled = true';
  const tokens = codeSyntaxSpans(code, 'toml').map(s=>[s.kind, code.slice(s.start,s.end)]);
  assert.deepEqual(tokens,[['comment','# 注释'],['key','model'],['string','"deepseek-v4-flash"'],['section','[model_providers.deepseek]'],['key','url'],['string','"https://example.test/#frag"'],['key','count'],['number','42'],['key','enabled'],['keyword','true']]);
});

test('highlighting never turns code into HTML and copy retains punctuation, indentation and Unicode', () => {
  const code = '  model = "<img onerror=bad()> & 中文"\n# `literal`';
  const html = renderSafeMarkdown('```toml\n'+code+'\n```');
  assert.ok(html.includes('chat-code-header'));
  assert.ok(html.includes('code-copy-label">复制'));
  assert.ok(html.includes('code-syntax-string'));
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('data-copy-text="  model = &quot;&lt;img onerror=bad()&gt; &amp; 中文&quot;\n# `literal`"'));
});

test('unknown and very large languages remain plain and find can span syntax tokens', () => {
  assert.deepEqual(codeSyntaxSpans('anything = "text"','unknown'),[]);
  assert.deepEqual(codeSyntaxSpans('x'.repeat(100001),'toml'),[]);
  const html=renderSafeMarkdown('```toml\nmodel = "x"\n```',{query:'model ='});
  assert.ok(html.includes('<mark>model =</mark>'));
});
