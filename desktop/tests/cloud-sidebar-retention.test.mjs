import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { renderChatFirstShell } from '../src/chat-shell.mjs';

const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const functions = source.slice(source.indexOf('function sameSetExceptId('), source.indexOf('function patchRetainedSidebarRows('));
const context = vm.createContext({ Set, Map });
vm.runInContext(functions, context);
const retain = (previous,next) => context.canRetainSidebar(previous,next);
const rows = [{ id:'synthetic-cloud', workspaceId:'synthetic-cloud-workspace', title:'合成云会话', revision:1, createdAt:'2026-01-01', messages:[] }];
const data = { summary:{id:'synthetic-local-workspace'},exchange:{conversations:[{...rows[0],id:'synthetic-local',workspaceId:undefined}],projects:[],knowledge:[],memory:[],relations:[]} };
const render = (list,keys) => renderChatFirstShell({ data, native:true, selectedConversationId:null, composerDraft:'',chatSearch:'',pane:'chat',status:'',error:'',connection:{},appearance:{mode:'system',fontSize:'standard',themeColor:'orange'},sidebarConversationList:list,cloudConversations:rows,syncedConversationKeys:new Set(keys),modelPickerNow:new Date('2026-01-01T12:00:00Z') });
const snapshot = { data, sidebarConversationList:'cloud', cloudConversations:rows,syncedConversationKeys:[],manualUnreadAtMs:new Map(),unreadConversationIds:new Set() };

test('account receipt refresh keeps the connected cloud sidebar because its rendered rows do not use local sync markers', () => {
 const keys=['synthetic-cloud-workspace:synthetic-cloud','synthetic-local-workspace:synthetic-local'];
 assert.equal(render('cloud',[]),render('cloud',keys));
 assert.equal((render('cloud',keys).match(/class="chat-history-cloud-sync"/g)||[]).length,0);
 assert.equal(retain(snapshot,{...snapshot,syncedConversationKeys:keys}),true);
});

test('local receipt changes still refresh the visible sync marker', () => {
 const keys=['synthetic-local-workspace:synthetic-local'];
 assert.notEqual(render('recent',[]),render('recent',keys));
 assert.equal((render('recent',keys).match(/class="chat-history-cloud-sync"/g)||[]).length,1);
 const local={...snapshot,sidebarConversationList:'recent'};
 assert.equal(retain(local,{...local,syncedConversationKeys:keys}),false);
});

test('changed cloud content, invalidated account, data owner or local-cloud navigation cannot retain stale rows', () => {
 for(const change of [{cloudConversations:[...rows]},{cloudConversations:[]},{data:{...data}},{sidebarConversationList:'recent'},{batchEditing:true},{manualUnreadAtMs:new Map([['synthetic-cloud',1]])}]) {
  assert.equal(retain(snapshot,{...snapshot,...change}),false);
 }
});
