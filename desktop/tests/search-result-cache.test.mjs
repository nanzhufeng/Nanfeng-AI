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


test('pagination cache separates offsets and never projects an incomplete catalog as a complete category', () => {
 const cache=createSearchResultCache();
 const args={query:'needle',category:'text'};
 cache.set({...args,offset:0},{hits:[{entryId:'first'}],totalCount:2,hasMore:true});
 assert.equal(cache.get({...args,offset:48}),null);
 cache.set({...args,offset:48},{hits:[{entryId:'second'}],totalCount:2,hasMore:false});
 assert.equal(cache.get({...args,offset:0}).hits[0].entryId,'first');
 assert.equal(cache.get({...args,offset:48}).hits[0].entryId,'second');
 cache.set({category:'all'},{hits:[{contentKind:'TEXT',entryId:'t'}],totalCount:60,hasMore:true});
 assert.equal(cache.get({category:'text'}),null);
 cache.set({category:'all'},{hits:[{contentKind:'TEXT',entryId:'t'}],totalCount:1,hasMore:false,indexCurrent:true});
 assert.equal(cache.get({category:'text'}).totalCount,1);
 assert.equal(cache.get({category:'text'}).hasMore,false);
 assert.equal(cache.get({category:'text',offset:48}),null);
});
