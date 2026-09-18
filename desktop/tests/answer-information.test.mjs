import test from 'node:test';
import assert from 'node:assert/strict';
import { answerInformation, renderAnswerInformation } from '../src/answer-information.mjs';

test('missing historical evidence is unknown, never provider failure or current settings', () => {
  assert.equal(answerInformation({ webSearchRequested: true, webSearchVerified: null }).networkLabel, '未记录（旧回答）');
  assert.equal(answerInformation(null).networkLabel, '未记录（旧回答）');
  assert.equal(answerInformation({ webSearchRequested: true, webSearchVerified: false }).networkLabel, '已请求，未返回联网依据');
  assert.equal(answerInformation({ webSearchRequested: false, webSearchVerified: true }).networkLabel, '已实际使用');
});

test('uses answer style and groups complete source titles without duplicates or style rows', () => {
  const record = { webSearchVerified: true, selectedSources: [
    {kind:'对话风格', title:'直言不讳'}, {kind:'个性化资料',title:'你的昵称'},
    {kind:'个性化资料',title:'你的职业'}, {kind:'Memory',title:'长期 Memory'},
    {kind:'资料库',title:'跨端开发规范'}, {kind:'资料库',title:'第二份资料'},
    {kind:'资料库',title:'跨端开发规范'}
  ] };
  const result = answerInformation(record);
  assert.equal(result.styleLabel, '直言不讳');
  assert.deepEqual(result.sources, [
    {label:'个性化资料',titles:['你的昵称','你的职业']},
    {label:'长期记忆',titles:['长期 Memory']},
    {label:'资料库',titles:['跨端开发规范','第二份资料']}
  ]);
  const html = renderAnswerInformation(record);
  assert.match(html, /回答设置/);
  assert.match(html, /知道了/);
  assert.doesNotMatch(html, /<strong>|class="icon-button close"/);
  assert.equal(answerInformation({selectedSources:[{kind:'对话风格',title:'基础风格和语气'}]}).styleLabel,'未记录（旧回答）');
});

test('source titles are escaped and missing sources stay empty', () => {
  assert.deepEqual(answerInformation(null).sources, []);
  const html = renderAnswerInformation({selectedSources:[{kind:'资料库',title:'<img src=x onerror=alert(1)>'}]});
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
});
