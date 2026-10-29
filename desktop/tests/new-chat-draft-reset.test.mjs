import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('new chat restores the unbound native draft and never revives browser-cache text', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const composerRouteOwner = source.slice(source.indexOf('// Composer drafts have exactly one route owner.'), source.indexOf('const MAX_COMPOSER_ATTACHMENT_BYTES'));
  const resetOwner=source.slice(source.indexOf('function resetNewConversationComposer('),source.indexOf('function scheduleOrdinaryComposerDraft('));
  const restoreOwner=source.slice(source.indexOf('function restoreOrdinaryComposerDraft('),source.indexOf('function resetNewConversationComposer('));
  assert.match(resetOwner, /resetNewConversationComposer\(\)[\s\S]{0,300}restoreOrdinaryComposerDraft\('新对话草稿未读取：'\)/);
  assert.match(restoreOwner,/operation = loadOrdinaryComposerDraft/);
  assert.match(restoreOwner,/operation\(\)\.then/);
  assert.doesNotMatch(source, /function resetNewConversationComposer\(\)[\s\S]{0,500}persistOrdinaryComposerDraft\(\{[\s\S]{0,200}text: ''/);
  assert.match(composerRouteOwner, /if \(action === 'new-chat'\)[\s\S]{0,300}resetNewConversationComposer\(\)/);
  assert.match(composerRouteOwner, /if \(action !== 'select-chat'\) return;[\s\S]{0,500}restoreOrdinaryComposerDraft\('普通会话草稿未读取：'\)/);
  assert.doesNotMatch(source, /function readChatDraft\(/);
  assert.doesNotMatch(source, /function readComposerAttachments\(/);
  assert.doesNotMatch(source, /nanfeng-ai\.desktop\.chat-draft\.v1/);
});
