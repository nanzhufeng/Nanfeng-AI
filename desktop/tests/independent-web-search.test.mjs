import test from 'node:test';
import assert from 'node:assert/strict';
import { usesIndependentSearch, createSearchServiceGrant, retrySearchMaterial } from '../src/independent-web-search.mjs';

test('old search service receipts remain readable without globally overriding new sends', async () => {
  const settings = [{ providerId: 'ZHIPU', enabled: false, credentialStored: true }];
  assert.equal(usesIndependentSearch(settings, [{ id: 'file-a' }], true), false);
  assert.equal(usesIndependentSearch(settings, [], true), false);
  assert.equal(usesIndependentSearch(settings, [{ id: 'file-a' }], false), false);
  assert.equal(usesIndependentSearch([], [{ id: 'file-a' }], true), false);
  const grant = await createSearchServiceGrant(' public material ', ['b', 'a']);
  assert.deepEqual(grant, await createSearchServiceGrant('public material', ['a', 'b']));
  assert.notEqual(grant.materialFingerprint, (await createSearchServiceGrant('changed material', ['a', 'b'])).materialFingerprint);
  assert.equal(grant.serviceId, 'ZHIPU_WEB_SEARCH'); assert.equal(grant.engineId, 'search_pro');
  assert.equal('modelId' in grant, false);
});

test('explicit retry recovers exact original materials through continuation without attachment names in text', () => {
  const messages = [{ id: 'u', role: 'user', blocks: [{ kind: 'TEXT', text: 'original question' }, { kind: 'ASSET_REF', asset: { id: 'a', displayName: 'local name' } }] },
    { id: 'a', parentId: 'u', role: 'assistant', attemptId: 'old' }, { id: 'b', parentId: 'a', role: 'assistant', attemptId: 'current' }];
  assert.deepEqual(retrySearchMaterial(messages, 'current'), { text: 'original question', attachments: [{ id: 'a', displayName: 'local name' }] });
  assert.equal(retrySearchMaterial(messages, 'missing'), null);
});
