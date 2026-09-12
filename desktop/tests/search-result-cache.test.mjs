import test from 'node:test';
import assert from 'node:assert/strict';
import { createSearchResultCache } from '../src/search-result-cache.mjs';
test('empty text and image tabs project the already loaded all catalog without blocking on another query', () => {
  const cache = createSearchResultCache();
  cache.set({category:'all'}, { hits: [{contentKind:'TEXT',entryId:'t'}, {contentKind:'IMAGE',entryId:'i'}, {contentKind:'FILE',entryId:'f',fileType:'pdf'}] });
  assert.deepEqual(cache.get({category:'text'}).hits.map(x=>x.entryId), ['t']);
  assert.deepEqual(cache.get({category:'image'}).hits.map(x=>x.entryId), ['i']);
  assert.equal(cache.get({category:'image',query:'new query'}), null);
  assert.equal(cache.get({category:'image',sortMode:'timeAscending'}), null);
  cache.clear();
  assert.equal(cache.get({category:'image'}), null);
});
