import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8').replaceAll('\r\n','\n');
const menuCode=source.slice(source.indexOf('const assistantMessageMenuTriggers ='),source.indexOf('function openConversationContextMenu('));
function fixture({selected='',role='assistant',id='m',target='body'}={}){
 let selection=selected;const opened=[],events=new Map(),range={selectNodeContents(node){this.node=node;}};
 const footer={textContent:'10月16日 Sol ¥1.0000'},message={id:'m',role,delivery:'COMPLETE',attemptId:'attempt',blocks:[{kind:'TEXT',text:'合成正文'}]};
 const conv={id:'c',currentLeafId:'m',messages:[message]},state={current:{summary:{id:'w'}},selectedConversationId:'c',assistantMessageMenu:null,error:''};
 const article={dataset:{messageId:id},querySelector:()=>null};
 const node={closest(selector){if(selector==='.chat-message[data-message-id]')return article;if(target==='footer'&&selector.includes('.chat-message-metadata'))return footer;if(target==='attachment'&&selector.includes('[data-attachment-id]'))return {};return null;}};
 const selectedRange={toString:()=>selection,removeAllRanges(){selection='';},addRange(r){selection=r.node.textContent;}};
 const context=vm.createContext({state,WeakSet,Number,window:{getSelection:()=>selectedRange},document:{createRange:()=>range},
  app:{addEventListener(type,handler){events.set(type,handler);}},resolveConversation:()=>conv,orderedConversationMessages:()=>conv.messages,
  menuAnchorRect:()=>({left:10,right:30,top:15,bottom:35}),closeTopOverlay(){state.assistantMessageMenu=null;},
  openTransientOverlay(kind,trigger,value){opened.push({kind,trigger,value});state.assistantMessageMenu=value;},
 });vm.runInContext(menuCode,context);
 const event=(extra={})=>({target:node,clientX:50,clientY:80,key:'',ctrlKey:false,metaKey:false,altKey:false,shiftKey:false,prevented:false,stopped:false,preventDefault(){this.prevented=true;},stopPropagation(){this.stopped=true;},...extra});
 return {context,state,opened,event,article,footer,events,get selection(){return selection;}};
}
test('footer context actions leave native copy/selection default available without opening a message menu',()=>{
 const f=fixture({target:'footer'}),e=f.event();assert.equal(f.context.handleMessageContextMenu(e),false);assert.equal(e.prevented,false);assert.equal(f.opened.length,0);
});
test('selected body text preserves its selection and does not trigger a custom message menu',()=>{
 const f=fixture({selected:'已选合成文字'}),e=f.event();assert.equal(f.context.handleMessageContextMenu(e),false);assert.equal(f.selection,'已选合成文字');assert.equal(e.prevented,false);assert.equal(f.opened.length,0);
});
test('unselected body context menu reuses the actual message owner and capability fields',()=>{
 const f=fixture(),e=f.event();assert.equal(f.context.handleMessageContextMenu(e),true);assert.equal(e.prevented,true);assert.equal(f.state.assistantMessageMenu.messageId,'m');assert.equal(f.state.assistantMessageMenu.canRegenerate,true);assert.equal(f.state.assistantMessageMenu.canChangeModel,true);assert.equal(f.state.assistantMessageMenu.anchorRect.left,50);
});
test('attachment-specific context menus and unknown/stale message identities retain their boundaries',()=>{
 for(const options of [{target:'attachment'},{id:'stale'},{role:'tool'}]){const f=fixture(options),e=f.event();assert.equal(f.context.handleMessageContextMenu(e),false);assert.equal(e.prevented,false);assert.equal(f.opened.length,0);}
});
test('repeated body contextmenu repositions an open menu while the explicit more button still toggles',()=>{
 const f=fixture({selected:''});f.context.handleMessageContextMenu(f.event());f.context.handleMessageContextMenu(f.event({clientX:70}));assert.equal(f.opened.length,2);assert.equal(f.state.assistantMessageMenu.anchorRect.left,70);f.context.toggleAssistantMessageMenu(f.article);assert.equal(f.state.assistantMessageMenu,null);
 const selected=fixture({selected:'已选文字'});selected.context.toggleAssistantMessageMenu(selected.article);assert.equal(selected.opened.length,1,'explicit button intent must remain available');
});
test('keyboard context menu anchors to the message control instead of the screen origin',()=>{
 const f=fixture(),e=f.event({clientX:0,clientY:0,key:'ContextMenu'});assert.equal(f.context.handleMessageContextMenu(e),true);assert.equal(f.state.assistantMessageMenu.anchorRect.left,10);
});
test('scoped Ctrl/Meta+A selects exactly this footer; copy keys remain untouched',()=>{
 for(const modifier of ['ctrlKey','metaKey']){const f=fixture({target:'footer'}),e=f.event({key:'a',[modifier]:true});assert.equal(f.context.selectMessageMetadata(e),true);assert.equal(f.selection,f.footer.textContent);assert.equal(e.prevented,true);assert.equal(e.stopped,true);const copy=f.event({key:'c',[modifier]:true});assert.equal(f.context.selectMessageMetadata(copy),false);assert.equal(copy.prevented,false);}
 const f=fixture(),e=f.event({key:'a',ctrlKey:true});assert.equal(f.context.selectMessageMetadata(e),false);assert.equal(e.prevented,false);
});
test('contextmenu and keyboard selection are registered at the existing app boundary',()=>{
 const f=fixture();assert.equal(typeof f.context.handleMessageContextMenu,'function');assert.equal(f.events.get('contextmenu'),f.context.handleMessageContextMenu);assert.equal(f.events.get('keydown'),f.context.selectMessageMetadata);
});
test('long model text remains in a keyboard-focusable metadata footer without changing its source',()=>{
 const model='合成长型号完整标记'.repeat(20),message={id:'m',role:'assistant',createdAt:'2026-10-16T00:00:00Z',modelSnapshot:{displayName:model},chargeMicros:1_000_000,currencyCode:'CNY',blocks:[{kind:'TEXT',text:'合成正文'}]};
 const data={summary:{id:'w'},exchange:{projects:[],knowledge:[],memory:[],relations:[],conversations:[{id:'c',title:'合成',revision:1,messages:[message]}]}};
 const html=renderChatFirstShell({data,native:true,selectedConversationId:'c',pane:'chat',status:'',error:'',connection:{}});assert.ok(html.includes(model));assert.match(html,/<p class="chat-message-metadata"[^>]*tabindex="0"/);assert.match(html,/aria-keyshortcuts="Control\+A Meta\+A"/);assert.equal(message.modelSnapshot.displayName,model);
});
test('opening PDF never makes a pointer callback reference keyboard-only variables',()=>{
 const start=source.indexOf("document.addEventListener('pointerdown', event => {\n  const row = event.target.closest?.('[data-conversation-row]');"),end=source.indexOf('\n}, true);',start)+11;
 let handler;const context=vm.createContext({state:{pdfPreview:{}},document:{addEventListener(_type,fn){handler=fn;}},conversationLongPress:{pointerDown(){throw new Error('not a sidebar row');}}});vm.runInContext(source.slice(start,end),context);assert.doesNotThrow(()=>handler({target:{closest:()=>null},key:undefined}));
});
test('PDF arrows use the actual keyboard owner and respect editing, modifier and repeat gates',()=>{
 const start=source.indexOf("window.addEventListener('keydown', event => {\n  const modifier = event.metaKey || event.ctrlKey;"),end=source.indexOf("window.addEventListener('resize'",start);let handler;const steps=[];
 const context=vm.createContext({state:{pdfPreview:{}},document:{activeElement:{tagName:'DIV'}},window:{addEventListener(_name,fn){handler=fn;}},navigatePdfPage:value=>steps.push(value)});vm.runInContext(source.slice(start,end),context);
 const event=(extra={})=>({target:{closest:()=>null},key:'ArrowRight',preventDefault(){},...extra});handler(event());handler(event({key:'ArrowLeft'}));handler(event({repeat:true}));handler(event({ctrlKey:true}));context.document.activeElement.tagName='INPUT';handler(event());assert.deepEqual(steps,[1,-1]);const images=[];context.document.activeElement.tagName='DIV';context.state.imagePreview={};context.navigateImagePreview=value=>images.push(value);handler(event());assert.deepEqual(steps,[1,-1]);assert.deepEqual(images,['next']);
});


for(const selected of [' ','    ','\t','\n\n','\u3000\u3000'])test(`whitespace selection is still a copyable body range: ${JSON.stringify(selected)}`,()=>{
 const f=fixture({selected}),e=f.event();assert.equal(f.context.handleMessageContextMenu(e),false);assert.equal(f.selection,selected);assert.equal(e.prevented,false);assert.equal(f.opened.length,0);
});

test('an already consumed body contextmenu retains its original owner and feedback',()=>{
 const f=fixture(),e=f.event({defaultPrevented:true});f.state.error='原控件反馈';assert.equal(f.context.handleMessageContextMenu(e),false);assert.equal(f.opened.length,0);assert.equal(f.state.error,'原控件反馈');
});

test('Ctrl/Meta+Shift+A is not reinterpreted as footer select-all',()=>{
 for(const modifier of ['ctrlKey','metaKey']){const f=fixture({selected:'原选区',target:'footer'}),e=f.event({key:'a',[modifier]:true,shiftKey:true});assert.equal(f.context.selectMessageMetadata(e),false);assert.equal(f.selection,'原选区');assert.equal(e.prevented,false);}
});
