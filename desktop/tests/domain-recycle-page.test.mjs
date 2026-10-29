import assert from 'node:assert/strict';
import test from 'node:test';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';

const data = { summary: { id: 'synthetic-workspace' }, exchange: {
  conversations: [{ id: 'conversation-deleted', title: '对话', deleted: true, revision: 2 }],
  knowledge: [{ id: 'knowledge-deleted', title: '<资料>', status: 'DELETED', revision: 4 },
    { id: 'knowledge-active', title: '活动知识', status: 'ACTIVE', revision: 1 }],
  memory: [{ id: 'memory-deleted', title: '记忆', status: 'DELETED', revision: 8 }],
  relations: [{ id: 'relation-revoked', fromId: 'first', toId: 'second', status: 'REVOKED', revision: 3 }],
} };

test('existing recycle entry exposes recoverable work records with current revision and safe titles', () => {
  const html = renderAndroidSettingsShell({ page: 'recycle', data });
  for (const [entity, id, revision] of [['knowledge', 'knowledge-deleted', 4], ['memory', 'memory-deleted', 8], ['relation', 'relation-revoked', 3]]) {
    assert.ok(html.includes(`data-action="restore" data-entity="${entity}" data-id="${id}" data-revision="${revision}"`));
  }
  assert.ok(html.includes('&lt;资料&gt;'));
  assert.ok(!html.includes('knowledge-active'));
  assert.ok(html.includes('data-action="restore-deleted-conversation"'));
  assert.ok(html.includes('清空会话回收站'));
  assert.match(html, /data-action="open-conversation-bulk-cleanup" data-scope="recycle" data-count="1"/);
});

test('archived and favorite pages do not expose deleted knowledge recovery or mix cleanup counts', () => {
  for (const page of ['archived', 'favorites', 'conversations']) {
    const html = renderAndroidSettingsShell({ page, data });
    assert.ok(!html.includes('data-entity="knowledge"'));
    assert.ok(!html.includes('domain-recycle-list'));
  }
});

test('only work records remain recoverable when no deleted conversations exist', () => {
  const html = renderAndroidSettingsShell({ page: 'recycle', data: { ...data, exchange: { ...data.exchange, conversations: [] } } });
  assert.ok(html.includes('knowledge-deleted'));
  assert.ok(!html.includes('open-conversation-bulk-cleanup'));
});

test('feature review names recovery entry and contextual button decision', () => {
  const html = renderAndroidSettingsShell({ page: 'feature-review' });
  assert.ok(html.includes('知识、记忆与关系回收站恢复'));
  assert.ok(html.includes('清空会话不会删除这些资料'));
});
