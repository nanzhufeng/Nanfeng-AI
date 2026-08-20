import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');

test('FB-P6-045 user bubbles are content-sized, right aligned, and only cap long text', () => {
  const userRules = css.slice(css.indexOf('.chat-message.user { justify-content: flex-end; }'), css.indexOf('.chat-message.assistant .chat-message-content'));
  assert.match(userRules, /\.chat-message\.user \{ justify-content: flex-end; \}/);
  assert.match(userRules, /width: fit-content/);
  assert.match(userRules, /max-width: min\(82%, 530px\)/);
  assert.match(userRules, /display: flex; flex-direction: column; align-items: flex-end/);
  assert.match(userRules, /\.chat-message\.user \.chat-message-bubble \{ width: fit-content; max-width: 100%/);
  assert.doesNotMatch(userRules, /min-width/);
  assert.doesNotMatch(userRules, /width: 82%/);
});
