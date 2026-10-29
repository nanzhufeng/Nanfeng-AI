import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, join, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

test('shell and runtime updates keep the reader connected with selection, focus and an open menu', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-preview-retention-'));
  try {
    const source = (await readFile(new URL('../src/runtime-chat-commit.mjs', import.meta.url), 'utf8')).replaceAll('export function', 'function');
    const shell = text => `<main><section class="chat-scroll" data-conversation-id="c"><p>${text}</p></section></main>`;
    const html = `${shell('原回答')}<section class="attachment-preview-overlay"><article style="height:100px;overflow:auto"><p id="selected">甲乙丙丁戊己</p>${'<p>长文阅读位置</p>'.repeat(80)}</article><details open><summary>文件</summary><div id="panel"><button>复制文本</button></div></details></section>`;
    const script = `${source}
      const app=document.querySelector('#app'),preview=app.querySelector('.attachment-preview-overlay');
      const article=preview.querySelector('article'),menu=preview.querySelector('details'),panel=preview.querySelector('#panel'),button=panel.querySelector('button');
      const supportsPopover=typeof panel.showPopover==='function';
      if(supportsPopover){panel.setAttribute('popover','manual');panel.showPopover();}
      button.focus({preventScroll:true});article.scrollTop=140;
      const text=preview.querySelector('#selected').firstChild,selection=getSelection();
      selection.setBaseAndExtent(text,5,text,1);
      const observer=new MutationObserver(()=>{});observer.observe(app,{childList:true,subtree:true});
      const shellAccepted=commitRetainedChatShell(app,${JSON.stringify(shell('更新后的回答'))},null,null,preview);
      const runtimeAccepted=commitRuntimeChatShell(app,${JSON.stringify(shell('流式新增回答'))},preview);
      const removals=observer.takeRecords().flatMap(record=>[...record.removedNodes]).filter(node=>node===preview||node.contains?.(preview)).length;
      observer.disconnect();
      const foreign=document.createElement('section');
      const facts={shellAccepted,runtimeAccepted,connected:app.contains(preview)&&preview.isConnected,
        text:selection.toString(),direction:selection.anchorNode===text&&selection.anchorOffset===5&&selection.focusNode===text&&selection.focusOffset===1,
        focus:document.activeElement===button,menu:menu.open,popover:!supportsPopover||panel.matches(':popover-open'),
        scroll:article.scrollTop,removals,updated:app.querySelector('.chat-scroll').textContent==='流式新增回答',
        foreignRejected:commitRetainedChatShell(app,${JSON.stringify(shell('不得写入'))},null,null,foreign)===false&&commitRuntimeChatShell(app,${JSON.stringify(shell('不得写入'))},foreign)===false};
      document.body.dataset.result=btoa(unescape(encodeURIComponent(JSON.stringify(facts))));`;
    await writeFile(join(root, 'index.html'), `<!doctype html><meta charset="utf-8"><div id="app">${html}</div><script type="module">${script}</script>`);
    const chrome = process.env.CHROME_PATH || (process.platform === 'win32' ? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' : '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome');
    const result = spawnSync(chrome, ['--headless=new', '--disable-gpu', '--no-default-browser-check', '--no-first-run', '--disable-background-networking', '--disable-sync', `--user-data-dir=${join(root, 'profile')}`, '--timeout=5000', '--virtual-time-budget=2000', '--dump-dom', pathToFileURL(join(root, 'index.html')).href], { encoding: 'utf8', timeout: 15000, maxBuffer: 4 * 1024 * 1024, windowsHide: true });
    assert.equal(result.status, 0, result.error?.message || result.stderr?.slice(-300));
    const encoded = result.stdout?.match(/data-result="([^"]+)"/)?.[1];
    assert.ok(encoded, `reader fixture did not finish: ${result.stderr?.slice(-300)}`);
    const facts = JSON.parse(Buffer.from(encoded, 'base64').toString('utf8'));
    assert.deepEqual(facts, { shellAccepted: true, runtimeAccepted: true, connected: true, text: '乙丙丁戊', direction: true, focus: true, menu: true, popover: true, scroll: 140, removals: 0, updated: true, foreignRejected: true });
  } finally {
    assert.ok(resolve(root).startsWith(resolve(tmpdir()) + sep) && basename(root).startsWith('nfai-preview-retention-'));
    await rm(root, { recursive: true, force: true });
  }
});
