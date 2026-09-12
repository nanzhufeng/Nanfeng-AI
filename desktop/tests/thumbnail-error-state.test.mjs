import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
test('failed thumbnail leaves loading state and exposes retry without rerendering', async () => {
  const source = await readFile(new URL('../src/app.mjs',import.meta.url),'utf8');
  const start = source.indexOf('function hydrateImageThumbnail');
  const end = source.indexOf("app.addEventListener('click'",start);
  const placeholder={textContent:'正在读取本地缩略图'};
  const element={dataset:{imageThumbnail:'fixture'},querySelector:()=>placeholder};
  const context=vm.createContext({document:{querySelectorAll:()=>[element]}});
  vm.runInContext(source.slice(start,end),context);
  context.hydrateImageThumbnail('fixture',{error:'failed'});
  assert.equal(placeholder.textContent,'图片读取失败，点击重试');
  assert.equal(element.dataset.retryImageThumbnail,'fixture');
});
