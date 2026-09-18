import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
import {mkdtemp,writeFile,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os'; import {join} from 'node:path'; import {pathToFileURL,fileURLToPath} from 'node:url'; import {spawn} from 'node:child_process';
const chrome='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
test('shell controls keep long conversations connected and settings navigation stays error free', { skip: !existsSync(chrome), timeout: 40000 }, async () => {
const dir=await mkdtemp(join(tmpdir(),'nfai-click-'));const src=fileURLToPath(new URL('../src/',import.meta.url));
const script=`try {
 const errors=[];addEventListener('error',e=>errors.push(String(e.error||e.message)));addEventListener('unhandledrejection',e=>errors.push(String(e.reason)));
 await import(${JSON.stringify(pathToFileURL(src+'app.mjs').href)});
 await new Promise(r=>setTimeout(r,100));
 const state=globalThis.__nanfengDesktopWorkState;
 const conversation={id:'interaction-fixture',title:'点击性能样本',revision:1,createdAt:'2026-09-18T00:00:00Z',messages:Array.from({length:150},(_,i)=>({id:'message-'+i,role:i%2?'assistant':'user',createdAt:'2026-09-18T00:00:00Z',blocks:[{kind:'TEXT',text:('第'+i+'段正文。').repeat(80)+'\\n\\n'+String.fromCharCode(96).repeat(3)+'js\\nconst example = 42;\\n'+String.fromCharCode(96).repeat(3)}]}))};
 state.current={summary:{id:'performance',name:'性能样本'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[],assets:[]}};state.selectedConversationId=conversation.id;state.pane='chat';
 const action=name=>{const button=document.querySelector('[data-action="'+name+'"]');if(!button)throw Error('missing '+name);button.click();};
 action('toggle-rail');await new Promise(r=>setTimeout(r,0));
 const scroll=document.querySelector('.chat-scroll');scroll.style.height='500px';scroll.style.flex='none';scroll.style.overflow='auto';scroll.getBoundingClientRect();scroll.scrollTop=600;const initialTop=scroll.scrollTop;
 let detachments=0;const observer=new MutationObserver(rs=>{for(const r of rs)for(const n of r.removedNodes)if(n===scroll||n.contains?.(scroll))detachments++});observer.observe(document.querySelector('#app'),{childList:true,subtree:true});
 const timing=[];for(const actionName of ['toggle-conversation-batch-edit','toggle-batch-conversation','request-batch-conversation-delete','close-dialog']){const start=performance.now();action(actionName);document.body.getBoundingClientRect();await new Promise(r=>setTimeout(r,0));timing.push({action:actionName,ms:performance.now()-start});}
 const retainedFacts={detachments,initialTop,same:scroll===document.querySelector('.chat-scroll'),top:scroll.scrollTop};
 const controls=[];
 const click=async selector=>{const button=document.querySelector(selector);if(!button)throw Error('missing '+selector);const start=performance.now();button.click();document.body.getBoundingClientRect();await new Promise(r=>setTimeout(r,0));controls.push({action:button.dataset.action,detail:button.dataset.page||button.dataset.picker||'',ms:performance.now()-start});};
 const act=name=>'[data-action="'+name+'"]';
 await click(act('toggle-batch-select-all'));await click(act('toggle-batch-select-all'));await click(act('toggle-conversation-batch-edit'));
 for(const name of ['toggle-composer-add','toggle-p6g-model-picker']){await click(act(name));await click(act(name));}
 await click(act('open-conversation-row-menu'));await click(act('context-menu-rename'));await click(act('close-dialog'));
 await click(act('open-conversation-row-menu'));await click(act('context-menu-delete'));await click(act('close-dialog'));
 const chatDetachments=detachments;observer.disconnect();
 await click(act('show-settings'));
 const seenPages=new Set(), queued=new Map();
 const collect=parent=>{for(const button of document.querySelectorAll('[data-action="open-settings-page"]'))if(!button.disabled&&!seenPages.has(button.dataset.page)&&!queued.has(button.dataset.page))queued.set(button.dataset.page,parent);};
 collect('personalization');
 for(let pass=0;queued.size&&pass<40;pass++){
  const [page,parent]=queued.entries().next().value;queued.delete(page);
  const selector=act('open-settings-page')+'[data-page="'+page+'"]';
  if(!document.querySelector(selector)&&parent)await click(act('open-settings-page')+'[data-page="'+parent+'"]');
  if(!document.querySelector(selector))throw Error('inaccessible settings page '+page);
  seenPages.add(page);await click(selector);collect(page);
 }
 for(const picker of ['mode','fontSize','themeColor']){await click(act('open-settings-picker')+'[data-picker="'+picker+'"]');await click(act('dismiss-settings-picker'));}
 await click(act('show-chat'));
 for(const name of ['show-reminders','show-transcription','show-work','show-projects','show-knowledge','show-memory','show-chat']){if(document.querySelector(act(name)))await click(act(name));}
 if(document.querySelector(act('open-full-search'))){await click(act('open-full-search'));}
 document.body.setAttribute('data-result',btoa(JSON.stringify({...retainedFacts,chatDetachments,timing,controls,pages:[...seenPages],errors}))); }
 catch(error){document.body.setAttribute('data-result',btoa(JSON.stringify({error:String(error),stack:error.stack})));}`;
const styles=['styles.css','chat-shell.css','image-preview.css'].map(x=>'<link rel="stylesheet" href="'+pathToFileURL(src+x).href+'">').join('');
await writeFile(join(dir,'index.html'),`<!doctype html><meta charset="utf-8">${styles}<main id="app" class="app-shell"></main><script type="module">${script}</script>`);
const child=spawn(chrome,['--headless=new','--allow-file-access-from-files','--disable-gpu','--no-first-run','--disable-background-networking','--disable-sync',`--user-data-dir=${dir}/profile`,'--remote-debugging-port=0','about:blank'],{stdio:'ignore'});
let ws;
try {
 let port; for(let i=0;i<100&&!port;i++){try{port=(await readFile(dir+'/profile/DevToolsActivePort','utf8')).split('\n')[0]}catch{await new Promise(r=>setTimeout(r,100))}}
 const tabs=await (await fetch('http://127.0.0.1:'+port+'/json/list')).json();
 ws=new WebSocket(tabs.find(t=>t.type==='page').webSocketDebuggerUrl);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});let id=0;const pending=new Map();ws.onmessage=e=>{const m=JSON.parse(e.data);if(pending.has(m.id)){const [r,j]=pending.get(m.id);pending.delete(m.id);m.error?j(m.error):r(m.result)}};
 const call=(method,params={})=>new Promise((r,j)=>{pending.set(++id,[r,j]);ws.send(JSON.stringify({id,method,params}))});
 await call('Emulation.setCPUThrottlingRate',{rate:4});await call('Page.navigate',{url:pathToFileURL(join(dir,'index.html')).href});
 let result;for(let i=0;i<150&&!result;i++){await new Promise(r=>setTimeout(r,100));result=(await call('Runtime.evaluate',{expression:'document.body?.getAttribute("data-result")',returnByValue:true})).result?.value;}
 assert.ok(result,'full application fixture must complete');
 const facts=JSON.parse(Buffer.from(result,'base64').toString());
 if(process.env.NANFENG_CLICK_REPORT)await writeFile(process.env.NANFENG_CLICK_REPORT,JSON.stringify(facts,null,2));
 assert.equal(facts.error,undefined);
 assert.deepEqual(facts.errors,[]);
 assert.equal(facts.chatDetachments,0);
 assert.ok(facts.pages.length>=17);
 assert.ok(facts.controls.length>=30);
 assert.equal(facts.detachments,0,'batch and dialog clicks must never detach the long transcript');
 assert.equal(facts.same,true);
 assert.equal(facts.top,facts.initialTop);
 assert.equal(facts.initialTop,600);
 assert.equal(facts.timing.length,4);
}finally{ws?.close();child.kill('SIGTERM');await new Promise(r=>child.once('exit',r));await rm(dir,{recursive:true,force:true})}

});
