import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('new chat clears the generic draft and render never revives legacy browser storage', async () => {
  const source = await readFile(new URL('../src/app.mjs', import.meta.url), 'utf8');
  assert.match(source, /if \(action === 'new-chat'\) \{[\s\S]{0,500}clearNewConversationDraft\(\)/);
  assert.match(source, /function clearNewConversationDraft\(\)[\s\S]{0,900}persistLegacyOrdinaryDraft\(\{ \.\.\.args, text: '', attachmentIds: \[\] \}\)[\s\S]{0,900}persistOrdinaryComposerDraft\(\{ \.\.\.args, text: '', attachmentIds: \[\] \}\)/);
  assert.doesNotMatch(source, /if \(state\.pane === 'chat' && !state\.temporaryConversation && !state\.composerDraft\) state\.composerDraft = readChatDraft\(\)/);
  assert.doesNotMatch(source, /if \(state\.pane === 'chat' && !state\.composerAttachments\.length\) state\.composerAttachments = readComposerAttachments\(\)/);
});
