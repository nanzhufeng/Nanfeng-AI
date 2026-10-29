import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const script=String.raw`
const fs=await import('node:fs/promises'),vm=await import('node:vm');
const {createDraftWriteQueue}=await import('./src/draft-write-queue.mjs');
const {readComposerDraftRoute}=await import('./src/composer-draft-recovery.mjs');
const source=await fs.readFile('./src/app.mjs','utf8');
const owner=source.slice(source.indexOf('const ordinaryComposerDraftQueue = createDraftWriteQueue('),source.indexOf('function writeComposerAttachments('));
const model=source.slice(source.indexOf('function newConversationModelId('),source.indexOf('\nasync function updateP6GConversationOverride('));
const listener=source.slice(source.indexOf('// Composer drafts have exactly one route owner.'),source.indexOf('const MAX_COMPOSER_ATTACHMENT_BYTES'));
let callback,healthy=false,reads=0,writes=[];const unhandled=[];process.on('unhandledRejection',e=>unhandled.push(String(e)));
const records=new Map([['CHAT:workspace:original',{text:'original stored text',attachments:[]}]]);
const state={dataArea:'CHAT',pane:'chat',dialog:null,current:{summary:{id:'workspace'}},selectedConversationId:'original',temporaryConversation:null,composerDraft:'  unsaved original\nline two  ',composerAttachments:[],pendingNewConversationModelId:null,conversationPreferences:{}};
let context;context=vm.createContext({state,native:true,createDraftWriteQueue,readComposerDraftRoute,structuredClone,desktopEntryRouteGeneration:0,
 setTimeout:fn=>{context.timer=fn;return 1;},clearTimeout(){context.timer=null;},render(){},
 tauriBridge:{invoke:async(command,payload,options)=>{if(command!=='save_desktop_conversation_draft')throw Error('Unexpected write');const args=payload.args;writes.push({area:options.headers['x-nanfeng-data-area'],...args});if(!healthy)throw Error('SOURCE_WRITE_FAILED');const d={text:args.text,attachments:[],modelId:args.modelId,toneOverride:args.toneOverride,webSearchOverride:args.webSearchOverride};records.set(options.headers['x-nanfeng-data-area']+':'+args.workspaceId+':'+(args.conversationId||''),d);return d;}},
 invoke:async(command,args)=>{if(command!=='read_desktop_conversation_draft')throw Error('Unexpected read');reads++;return records.get('CHAT:'+args.workspaceId+':'+(args.conversationId||''))||null;},
 app:{addEventListener(_name,handler){callback=handler;}}});
vm.runInContext(model+'\n'+owner+'\n'+listener+'\nglobalThis.draftOwner={scheduleOrdinaryComposerDraft,flushOrdinaryComposerDraft};',context);
context.draftOwner.scheduleOrdinaryComposerDraft('text');
state.selectedConversationId=null;state.composerDraft='';context.desktopEntryRouteGeneration++;
const mode=process.env.NFAI_FLUSH_TEST_MODE;
if(mode==='temporary')state.temporaryConversation={temporaryId:'synthetic-temporary'};
if(mode==='selected')state.selectedConversationId='destination';
if(mode==='non-native')context.native=false;
callback({target:{closest:()=>({dataset:{action:'new-chat'}})}});
state.composerDraft='new current input';
if(mode==='navigation'){context.desktopEntryRouteGeneration++;state.error='current destination status';}
if(mode==='dialog'){state.dialog={kind:'new-dialog'};state.error='current destination status';}
await new Promise(resolve=>setTimeout(resolve,50));
const before={unhandled,writesBeforeRecovery:writes.length,reads,error:state.error,newInput:state.composerDraft,oldStoredBeforeRecovery:records.get('CHAT:workspace:original').text};
healthy=true;await context.draftOwner.flushOrdinaryComposerDraft();
console.log(JSON.stringify({...before,recovered:records.get('CHAT:workspace:original').text,writeRoutes:writes.map(x=>[x.area,x.workspaceId,x.conversationId]),newInputAfterRecovery:state.composerDraft}));
`;
for(const mode of ['ordinary','temporary','selected','non-native','navigation','dialog'])test(`new-chat ${mode} handles one failing source flush without unhandled rejection`,()=>{
 const child=spawnSync(process.execPath,['--max-old-space-size=2048','--input-type=module','-e',script],{cwd:root,windowsHide:true,encoding:'utf8',timeout:15000,env:{...process.env,NFAI_FLUSH_TEST_MODE:mode}});
 assert.equal(child.status,0,child.stderr);const r=JSON.parse(child.stdout.trim());
 assert.deepEqual(r.unhandled,[]);assert.equal(r.writesBeforeRecovery,1);assert.equal(r.reads,0);
 if(mode==='navigation'||mode==='dialog')assert.equal(r.error,'current destination status');
 else assert.match(r.error,/SOURCE_WRITE_FAILED/);
 assert.equal(r.oldStoredBeforeRecovery,'original stored text');
 assert.equal(r.recovered,'  unsaved original\nline two  ');assert.equal(r.newInput,'new current input');assert.equal(r.newInputAfterRecovery,'new current input');
 assert.ok(r.writeRoutes.every(x=>x.join(':')==='CHAT:workspace:original'));
});
