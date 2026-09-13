import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('new chat has one native draft owner and never revives browser-cache text', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  const composerRouteOwner = source.slice(source.indexOf('// Composer drafts have exactly one route owner.'), source.indexOf('const MAX_COMPOSER_ATTACHMENT_BYTES'));
  assert.match(source, /function resetNewConversationComposer\(\)[\s\S]{0,700}persistOrdinaryComposerDraft\(\{[\s\S]{0,200}conversationId: null,[\s\S]{0,200}text: ''/);
  assert.match(composerRouteOwner, /if \(action === 'new-chat'\)[\s\S]{0,300}resetNewConversationComposer\(\)/);
  assert.match(composerRouteOwner, /if \(action !== 'select-chat'\) return;[\s\S]{0,500}loadOrdinaryComposerDraft\(\)\.then\(render\)/);
  assert.doesNotMatch(source, /function readChatDraft\(/);
  assert.doesNotMatch(source, /function readComposerAttachments\(/);
  assert.doesNotMatch(source, /nanfeng-ai\.desktop\.chat-draft\.v1/);
});
