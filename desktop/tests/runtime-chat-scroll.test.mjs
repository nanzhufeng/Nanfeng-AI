import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

test('stream updates and background title waits keep the live scroll owner, reading position and selection', async () => {
  const root = await mkdtemp(join(tmpdir(), 'nfai-runtime-scroll-'));
  try {
    const implementation = await readFile(new URL('../src/runtime-chat-commit.mjs', import.meta.url), 'utf8');
    const css = await readFile(new URL('../src/chat-shell.css', import.meta.url), 'utf8');
    const conversation = { id: 'scroll-fixture', title: '新对话', revision: 1, messages: [
      { id: 'user', role: 'user', blocks: [{ kind: 'TEXT', text: '演示代码与表格' }] },
      { id: 'answer', role: 'assistant', delivery: 'PARTIAL', blocks: [{ kind: 'TEXT', text: Array.from({ length: 80 }, (_, i) => `第 ${i} 段阅读内容。`).join('\n\n') + '\n\n```js\n' + 'const example = 123; '.repeat(80) + '\n```' }] },
    ] };
    const shell = () => renderChatFirstShell({ data: { summary: { id: 'fixture' }, exchange: { conversations: [conversation] } }, selectedConversationId: conversation.id, pane: 'chat', native: true, connection: {}, status: '', error: '' });
    const first = shell();
    conversation.messages[1].blocks[0].text += '\n\n新到达的末尾段落';
    const streamed = shell();
    conversation.title = '阅读滚动验证'; conversation.messages[1].delivery = 'COMPLETE';
    const terminal = shell();
    const script = `${implementation.replaceAll('export function', 'function')}
      const app=document.querySelector('#app');
      if(typeof installRuntimeChatScrolling==='function') installRuntimeChatScrolling(app);
      const original=app.querySelector('.chat-scroll'); original.scrollTop=500;
      const early=app.querySelector('[data-message-id="answer"] p');
      const selected=document.createRange(); selected.selectNodeContents(early); getSelection().addRange(selected);
      const selectionBefore=getSelection().toString();
      const code=app.querySelector('pre'); code.scrollLeft=100;
      let removals=0; const observer=new MutationObserver(records=>{for(const r of records)for(const n of r.removedNodes)if(n===original||n.contains?.(original))removals++;}); observer.observe(app,{subtree:true,childList:true});
      const frames=${JSON.stringify([first, streamed, streamed, terminal, terminal])};
      let stable=true, position=true, horizontal=true;
      for(const frame of frames){
        const before=original.scrollTop;
        commitRuntimeChatShell(app,frame);
        stable &&= original.isConnected && app.querySelector('.chat-scroll')===original;
        position &&= Math.abs(original.scrollTop-before)<1;
        horizontal &&= code.isConnected && code.scrollLeft===100;
        original.scrollTop+=60;
      }
      await new Promise(r=>setTimeout(r,0));
      const selectedStill=getSelection().toString()===selectionBefore;
      const visibleTitle=app.querySelector('h1')?.textContent==='阅读滚动验证';
      const newText=app.textContent.includes('新到达的末尾段落');
      original.scrollTop=original.scrollHeight;
      const longer=${JSON.stringify(terminal)}.replace('新到达的末尾段落','新到达的末尾段落'+ '<p>继续生成</p>'.repeat(20));
      commitRuntimeChatShell(app,longer);
      const followsBottom=Math.abs(original.scrollHeight-original.clientHeight-original.scrollTop)<1;
      original.dispatchEvent(new WheelEvent('wheel',{deltaY:-4,bubbles:true}));
      original.scrollTop-=4;
      const gentleTop=original.scrollTop;
      commitRuntimeChatShell(app,longer);
      const gentleScrollWins=Math.abs(original.scrollTop-gentleTop)<1;
      original.scrollTop-=160;
      const manualTop=original.scrollTop;
      commitRuntimeChatShell(app,longer.replace('继续生成','末尾继续增长'));
      const manualReadingWins=Math.abs(original.scrollTop-manualTop)<1;
      const switched=commitRuntimeChatShell(app,${JSON.stringify(terminal.replaceAll('scroll-fixture','another-conversation'))});
      document.body.dataset.result=btoa(JSON.stringify({stable,position,horizontal,selectedStill,visibleTitle,newText,removals,switched,followsBottom,manualReadingWins,gentleScrollWins}));`;
    await writeFile(join(root, 'index.html'), `<!doctype html><meta charset="utf-8"><style>${css} .chat-scroll{height:400px;overflow:auto}</style><div id="app" class="app-shell chat-first">${first}</div><script type="module">${script}</script>`);
    const result = spawnSync('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless=new', '--disable-gpu', '--no-default-browser-check', '--no-first-run', '--disable-background-networking', '--disable-sync', `--user-data-dir=${join(root, 'profile')}`, '--timeout=5000', '--virtual-time-budget=2000', '--dump-dom', pathToFileURL(join(root, 'index.html')).href], { encoding: 'utf8', timeout: 15000, maxBuffer: 4 * 1024 * 1024 });
    assert.equal(result.status, 0, `browser exit: ${result.error?.message || result.stderr?.slice(-150)}`);
    const encoded = result.stdout?.match(/data-result="([^"]+)"/)?.[1];
    assert.ok(encoded, `browser fixture did not finish: ${result.stderr?.slice(-300)}`);
    const facts = JSON.parse(Buffer.from(encoded, 'base64').toString());
    assert.deepEqual(facts, { stable: true, position: true, horizontal: true, selectedStill: true, visibleTitle: true, newText: true, removals: 0, switched: false, followsBottom: true, manualReadingWins: true, gentleScrollWins: true });
  } finally { await rm(root, { recursive: true, force: true }); }
});
