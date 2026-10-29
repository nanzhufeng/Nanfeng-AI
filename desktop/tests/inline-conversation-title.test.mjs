import test from 'node:test';
import assert from 'node:assert/strict';
import { createInlineTitleEditor, inlineTitleInput } from '../src/inline-conversation-title.mjs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const settle=()=>new Promise(resolve=>setImmediate(resolve));
function harness({mutate=async()=>({localSaved:true}),prepare=async()=>true,readCurrent}={}) {
 const state={dataArea:'CHAT',current:{summary:{id:'w'}},selectedConversationId:'c',pane:'chat',dialog:null};
 let owner=state,route=0,renders=0;
 const listeners=new Map(),document={activeElement:null};
 const host={querySelector:()=>input,addEventListener:(name,handler)=>listeners.set(name,handler)};
 function makeInput(value='旧标题') {return {value,isConnected:true,selectionStart:1,selectionEnd:3,selectionDirection:'backward',matches:()=>true,focus(){document.activeElement=this;},select(){this.selected=true;},setSelectionRange(...args){this.range=args;}};}
 let input=makeInput();const calls=[];
 const editor=createInlineTitleEditor({host,document,getState:()=>owner,routeKey:()=>String(route),prepare,readCurrent,native:true,resolveHeader:()=>null,
  render(){renders++;},mutate:async(...args)=>{calls.push(args);return mutate(...args);}});
 return {editor,state,document,listeners,calls,makeInput,get input(){return input;},set input(value){input=value;},get renders(){return renders;},navigate(){route++;},replaceState(){owner={...state,inlineConversationRename:null};return owner;},
  begin:()=>editor.begin({id:'c',revision:3,title:'旧标题',surface:'header'}),
  event(name,extra={}){const event={type:name,target:input,preventDefault(){this.prevented=true;},stopPropagation(){},...extra};listeners.get(name)?.(event);return event;}};
}
test('explicit entry selects once; Enter trims a title and uses the captured revision',async()=>{
 const h=harness();await h.begin();await settle();assert.equal(h.input.selected,true);
 h.input.value='  新标题  ';h.event('input');const event=h.event('keydown',{key:'Enter'});assert.equal(event.prevented,true);await settle();
 assert.equal(h.calls.length,1);assert.deepEqual(h.calls[0][0].dataset,{id:'c',revision:'3'});assert.deepEqual(h.calls[0][2],{title:'新标题'});assert.equal(h.state.inlineConversationRename,null);
});
test('Escape and an unchanged blurred title produce no mutation',async()=>{
 const h=harness();await h.begin();h.event('keydown',{key:'Escape'});assert.equal(h.state.inlineConversationRename,null);
 await h.begin();await h.editor.commit(h.input);assert.equal(h.calls.length,0);
});

test('120 Unicode scalars commit exactly, including supplementary characters',async()=>{
 const h=harness();await h.begin();const title='𠮷😀'.repeat(60);h.input.value='  '+title+'  ';h.event('input');await h.editor.commit(h.input);
 assert.equal(h.calls.length,1);assert.equal(h.calls[0][2].title,title);assert.equal(h.state.inlineConversationRename,null);
});

test('121 Unicode scalars keep the exact draft without writing a shortened title',async()=>{
 const h=harness();await h.begin();const title='😀'.repeat(121);h.input.value=title;h.event('input');await h.editor.commit(h.input);
 assert.equal(h.calls.length,0);assert.equal(h.state.inlineConversationRename.draft,title);assert.match(h.state.inlineConversationRename.error,/最多120/);
 h.event('keydown',{key:'Escape'});assert.equal(h.calls.length,0);assert.equal(h.state.inlineConversationRename,null);
});
test('empty title and revision rejection preserve the exact unsaved draft',async()=>{
 const h=harness({mutate:async()=>({localSaved:false,error:'REVISION_CONFLICT：标题已改变'})});await h.begin();
 h.input.value='  ';await h.editor.commit(h.input);assert.equal(h.calls.length,0);assert.match(h.state.inlineConversationRename.error,/不能为空/);
 h.input.value='  冲突时的草稿  ';h.event('input');await h.editor.commit(h.input);
 assert.equal(h.state.inlineConversationRename.draft,'  冲突时的草稿  ');assert.equal(h.state.inlineConversationRename.revision,3);assert.match(h.state.inlineConversationRename.error,/REVISION_CONFLICT/);assert.equal(h.calls.length,1);
});
test('Enter and focusout share one pending write; a local save stays saved when sync fails',async()=>{
 let release;const h=harness({mutate:()=>new Promise(resolve=>release=resolve)});await h.begin();h.input.value='一次写入';
 h.event('keydown',{key:'Enter'});h.event('focusout');assert.equal(h.calls.length,1);
 assert.equal(h.input.readOnly,true);
 release({localSaved:true,error:'同步失败'});await settle();assert.equal(h.state.inlineConversationRename,null);
});
test('composition Enter/229 and blur do not save a partial candidate; composition end saves final text',async()=>{
 const h=harness();await h.begin();h.event('compositionstart');h.input.value='拼音';
 h.event('keydown',{key:'Enter',isComposing:true});h.event('keydown',{key:'Enter',keyCode:229});h.event('focusout');assert.equal(h.calls.length,0);
 h.input.value='完整中文标题';h.event('compositionend');await settle();assert.equal(h.calls.length,1);assert.equal(h.calls[0][2].title,'完整中文标题');
});
test('refresh preserves selection direction and defers replacing an active IME input',async()=>{
 const h=harness();await h.begin();await settle();h.document.activeElement=h.input;
 assert.equal(h.editor.prepareRender(),false);const next=h.makeInput();h.input=next;await settle();assert.deepEqual(next.range,[1,3,'backward']);
 h.event('compositionstart');const renders=h.renders;assert.equal(h.editor.prepareRender(),true);assert.equal(h.renders,renders);
 h.event('compositionend');await settle();assert.equal(h.renders,renders+1);
});
test('a late failure after same-owner navigation cannot repaint or replace the next editor',async()=>{
 let reject;const h=harness({mutate:()=>new Promise((_,fail)=>reject=fail)});await h.begin();h.input.value='旧会话新标题';const saving=h.editor.commit(h.input);h.navigate();h.editor.reconcile();
 const afterNavigation=h.renders;reject(new Error('旧回执'));await saving;
 assert.equal(h.state.inlineConversationRename,null);assert.equal(h.renders,afterNavigation);assert.equal(h.calls[0][4].presentationCurrent(),false);
});

for(const [label,change] of [['dialog',s=>s.dialog={kind:'new-dialog'}],['batch',s=>s.batchEditing=true],['read-only',s=>s.settingsConversationReturn={readOnly:true}]])
test(`a rejected title save cannot repaint a newer ${label} presentation`,async()=>{
 let reject;const h=harness({mutate:()=>new Promise((_,fail)=>reject=fail)});
 await h.begin();h.input.value='原会话标题修改';const saving=h.editor.commit(h.input);
 change(h.state);h.editor.reconcile();const renders=h.renders;
 h.state.error='当前页面状态';
 reject(new Error('旧标题保存失败'));await saving;
 assert.equal(h.renders,renders);assert.equal(h.state.error,'当前页面状态');
 assert.equal(h.state.inlineConversationRename,null);assert.equal(h.calls.length,1);
 assert.equal(h.calls[0][4].presentationCurrent(),false);
});

test('a current thrown save failure retains exact title text and permits an explicit retry',async()=>{
 let healthy=false;const h=harness({mutate:async()=>{if(!healthy)throw new Error('CURRENT_SAVE_FAILED');return {localSaved:true};}});
 await h.begin();h.input.value='  准确草稿  ';h.event('input');await h.editor.commit(h.input);
 assert.equal(h.state.inlineConversationRename.draft,'  准确草稿  ');
 assert.match(h.state.inlineConversationRename.error,/CURRENT_SAVE_FAILED/);
 assert.equal(h.input.readOnly,false);assert.equal(h.state.inlineConversationRename.saving,false);
 healthy=true;await h.editor.commit(h.input);assert.equal(h.calls.length,2);assert.equal(h.state.inlineConversationRename,null);
 assert.deepEqual(h.calls.map(x=>x[2]),[{title:'准确草稿'},{title:'准确草稿'}]);
});
test('delayed entry is discarded after navigation or area replacement',async()=>{
 let release;const h=harness({prepare:()=>new Promise(resolve=>release=resolve)});const opening=h.begin();h.navigate();release(true);await opening;assert.equal(h.state.inlineConversationRename,undefined);
 const second=h.begin();const work=h.replaceState();release(true);await second;assert.equal(work.inlineConversationRename,null);
});

test('opening failure stays on the initiating page and cannot escape into a new route',async()=>{
 let reject;const h=harness({prepare:()=>new Promise((_,fail)=>reject=fail)});
 const opening=h.begin();h.navigate();const renders=h.renders;
 reject(new Error('旧会话读取失败'));
 await assert.doesNotReject(opening);
 assert.equal(h.state.error,undefined);assert.equal(h.renders,renders);
 const current=h.begin();reject(new Error('当前会话读取失败'));await assert.doesNotReject(current);
 assert.match(h.state.error,/标题编辑未打开.*当前会话读取失败/);
 assert.equal(h.state.inlineConversationRename,undefined);
});

test('cancelled or replaced opening cannot report an error over the next editor',async()=>{
 let reject;const h=harness({prepare:()=>new Promise((_,fail)=>reject=fail)});
 const cancelled=h.begin();h.editor.cancel();reject(new Error('已取消'));await assert.doesNotReject(cancelled);
 assert.equal(h.state.error,undefined);
 const old=h.begin();const work=h.replaceState();reject(new Error('CHAT读取失败'));await assert.doesNotReject(old);
 assert.equal(work.error,undefined);assert.equal(work.inlineConversationRename,null);
});

test('a delayed entry cannot reopen over a newer dialog, batch editor, or read-only view',async()=>{
 for(const change of [s=>s.dialog={kind:'new-dialog'},s=>s.batchEditing=true,s=>s.settingsConversationReturn={readOnly:true}]){
  let release;const h=harness({prepare:()=>new Promise(resolve=>release=resolve)});const opening=h.begin();change(h.state);
  const renders=h.renders;release(true);await opening;
  assert.equal(h.state.inlineConversationRename,undefined);assert.equal(h.renders,renders);
 }
});
test('rendered input safely escapes a long draft and exposes failure without a nested button',()=>{
 const html=inlineTitleInput({draft:'<img src=x onerror=alert(1)>"'+ '长'.repeat(100),error:'冲突，草稿已保留',saving:false});
 assert.doesNotMatch(html,/<img|<button/);assert.match(html,/&lt;img/);assert.match(html,/aria-invalid="true"/);assert.match(html,/role="alert"/);
});
test('real shell renders header/local/cloud edits only for their exact target and suppresses read-only/batch edits',()=>{
 const conversation={id:'c',title:'旧标题',revision:3,createdAt:'2026-01-01',messages:[]};
 const data={summary:{id:'w'},exchange:{conversations:[conversation],projects:[],knowledge:[],memory:[],relations:[]}};
 const base={data,native:true,selectedConversationId:'c',composerDraft:'',chatSearch:'',pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'},modelPickerNow:new Date('2026-01-01T12:00:00Z')};
 const edit={id:'c',workspaceId:null,surface:'sidebar',draft:'正在编辑',error:''};
 const render=overrides=>renderChatFirstShell({...base,inlineRename:edit,...overrides});
 assert.equal((render({}).match(/data-inline-conversation-rename/g)||[]).length,1);
 assert.ok(render({}).includes('<div class="chat-history-select"'));
 assert.equal(render({batchEditing:true}).includes('data-inline-conversation-rename'),false);
 const cloud={...conversation,workspaceId:'cloud-w'};
 assert.equal(render({sidebarConversationList:'cloud',cloudConversations:[cloud]}).includes('data-inline-conversation-rename'),false);
 assert.equal((render({sidebarConversationList:'cloud',cloudConversations:[cloud],inlineRename:{...edit,workspaceId:'cloud-w'}}).match(/data-inline-conversation-rename/g)||[]).length,1);
 assert.equal((render({inlineRename:{...edit,surface:'header'}}).match(/data-inline-conversation-rename/g)||[]).length,1);
 assert.equal(render({inlineRename:{...edit,surface:'header'},settingsConversationReturn:{readOnly:true,conversation,label:'回收站'}}).includes('data-inline-conversation-rename'),false);
});


test('one physical double-click sequence prepares the title source only once',async()=>{
 let prepared=0,release;const h=harness({prepare:()=>{prepared++;return new Promise(done=>release=done);}});
 const row={dataset:{id:'c',revision:'3',workspaceId:'w',archived:'false'}};
 const title={textContent:'旧标题',closest:()=>row};
 const target={matches:()=>false,closest:selector=>selector==='.chat-history-title'?title:null};
 const event={target,detail:2,stopImmediatePropagation(){}};
 h.event('click',event);h.event('dblclick',event);
 assert.equal(prepared,1,'the second click and ensuing dblclick are one user gesture');
 release(true);await settle();assert.equal(h.state.inlineConversationRename.id,'c');
 h.input.value='正在编辑的新标题';h.event('input');
 h.event('dblclick',event);await settle();
 assert.equal(prepared,1,'an already open same editor must keep the current draft');
 assert.equal(h.state.inlineConversationRename.draft,'正在编辑的新标题');
});


test('cancel and reopen is a new preparation even for the same title target',async()=>{
 const releases=[];const h=harness({prepare:()=>new Promise(done=>releases.push(done))});
 const first=h.begin();h.editor.cancel();const next=h.begin();assert.equal(releases.length,2);
 releases[0](true);await first;assert.equal(h.state.inlineConversationRename,null);
 releases[1](true);await next;assert.equal(h.state.inlineConversationRename.id,'c');
});


test('revision conflict reloads the current title and only explicit Enter saves the preserved draft against its revision',async()=>{
 let reads=0;const h=harness({readCurrent:async()=>{reads++;return {id:'c',revision:4,title:'另一次操作的标题'};},mutate:async target=>Number(target.dataset.revision)===3?{localSaved:false,error:'REVISION_CONFLICT'}:{localSaved:true}});
 await h.begin();h.input.value='  我的改名草稿  ';h.event('input');await h.editor.commit(h.input);
 assert.equal(reads,1);assert.equal(h.state.inlineConversationRename.draft,'  我的改名草稿  ');assert.match(h.state.inlineConversationRename.error,/另一次操作的标题/);assert.match(h.state.inlineConversationRename.error,/Enter/);
 h.event('focusout');await settle();assert.equal(h.calls.length,1,'blur is not permission to overwrite a conflicting title');
 h.event('keydown',{key:'Enter'});await settle();assert.equal(h.calls.length,2);assert.equal(h.calls[1][0].dataset.revision,'4');assert.deepEqual(h.calls[1][2],{title:'我的改名草稿'});assert.equal(h.state.inlineConversationRename,null);
});

test('a second concurrent title change requires another acknowledgement rather than an automatic write',async()=>{
 let revision=4;const h=harness({readCurrent:async()=>({id:'c',revision,title:'外部标题'+revision}),mutate:async()=>({localSaved:false,error:'REVISION_CONFLICT'})});
 await h.begin();h.input.value='保留草稿';await h.editor.commit(h.input);revision=5;
 h.event('keydown',{key:'Enter'});await settle();assert.equal(h.calls.length,2);assert.equal(h.calls[1][0].dataset.revision,'4');assert.match(h.state.inlineConversationRename.error,/外部标题5/);
 h.event('focusout');await settle();assert.equal(h.calls.length,2);assert.equal(h.state.inlineConversationRename.draft,'保留草稿');
});

test('failed conflict read keeps the draft and blocks blur; Enter retries the read before any further write',async()=>{
 let fail=true,reads=0;const h=harness({readCurrent:async()=>{reads++;if(fail)throw new Error('SYNTHETIC_READ_FAILED');return {id:'c',revision:4,title:'当前标题'};},mutate:async()=>({localSaved:false,error:'REVISION_CONFLICT'})});
 await h.begin();h.input.value='  精确保留  ';await h.editor.commit(h.input);assert.match(h.state.inlineConversationRename.error,/草稿已保留/);
 h.event('focusout');await settle();assert.equal(h.calls.length,1);fail=false;h.event('keydown',{key:'Enter'});await settle();assert.equal(reads,2);assert.equal(h.calls.length,1);assert.match(h.state.inlineConversationRename.error,/当前标题/);assert.equal(h.state.inlineConversationRename.draft,'  精确保留  ');
});

test('late conflict read after navigation cannot replace or repaint another editor',async()=>{
 let release;const pending=new Promise(r=>release=r);const h=harness({readCurrent:()=>pending,mutate:async()=>({localSaved:false,error:'REVISION_CONFLICT'})});
 await h.begin();h.input.value='旧会话草稿';const saving=h.editor.commit(h.input);await settle();h.navigate();h.editor.reconcile();const renders=h.renders;
 release({id:'c',revision:4,title:'迟到标题'});await saving;assert.equal(h.state.inlineConversationRename,null);assert.equal(h.renders,renders);
});


test('Escape cancels a pending conflict read without waiting for its late snapshot',async()=>{
 let release;const pending=new Promise(r=>release=r);const h=harness({readCurrent:()=>pending,mutate:async()=>({localSaved:false,error:'REVISION_CONFLICT'})});
 await h.begin();h.input.value='等待核对的草稿';const saving=h.editor.commit(h.input);await settle();h.event('keydown',{key:'Escape'});assert.equal(h.state.inlineConversationRename,null);
 const renders=h.renders;release({id:'c',revision:4,title:'迟到的新标题'});await saving;assert.equal(h.state.inlineConversationRename,null);assert.equal(h.renders,renders);
});


test('IME final Enter keeps the completed Chinese title editable until a later explicit Enter',async()=>{
 const h=harness();await h.begin();h.event('compositionstart',{timeStamp:1000});h.input.value='完整中文标题';h.event('input');h.event('compositionend',{timeStamp:1100});
 h.event('keydown',{key:'Enter',keyCode:13,isComposing:false,timeStamp:1100});await settle();
 assert.equal(h.calls.length,0,'IME confirmation is not a rename confirmation');assert.equal(h.state.inlineConversationRename.draft,'完整中文标题');
 h.event('keydown',{key:'Enter',keyCode:13,isComposing:false,timeStamp:1200});await settle();assert.equal(h.calls.length,1);assert.equal(h.calls[0][2].title,'完整中文标题');
});

test('IME tail Escape keeps the editor and exact draft; a later explicit Escape cancels',async()=>{
 const h=harness();await h.begin();h.event('compositionstart',{timeStamp:2000});h.input.value='仍需继续编辑';h.event('input');h.event('compositionend',{timeStamp:2100});
 h.event('keydown',{key:'Escape',keyCode:27,isComposing:false,timeStamp:2199});await settle();assert.equal(h.calls.length,0);assert.equal(h.state.inlineConversationRename?.draft,'仍需继续编辑');
 h.event('keydown',{key:'Escape',keyCode:27,isComposing:false,timeStamp:2200});assert.equal(h.state.inlineConversationRename,null);assert.equal(h.calls.length,0);
});


test('an explicit blur after IME completion still saves the final title immediately',async()=>{
 const h=harness();await h.begin();h.event('compositionstart',{timeStamp:3000});h.input.value='失焦保存完整中文';h.event('input');h.event('compositionend',{timeStamp:3100});h.event('focusout',{timeStamp:3101});await settle();assert.equal(h.calls.length,1);assert.equal(h.calls[0][2].title,'失焦保存完整中文');
});

test('IME tail timestamps never leak into a replacement editor',async()=>{
 const h=harness();await h.begin();h.event('compositionstart',{timeStamp:4000});h.event('compositionend',{timeStamp:4100});h.editor.cancel();await h.begin();h.input.value='新编辑独立确认';h.event('input');h.event('keydown',{key:'Enter',timeStamp:4101});await settle();assert.equal(h.calls.length,1);assert.equal(h.calls[0][2].title,'新编辑独立确认');
});
