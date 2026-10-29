import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { renderDesktopRemindersPage } from '../src/desktop-reminders-page.mjs';
import { renderAndroidSettingsShell } from '../src/android-settings-shell.mjs';
const source = (await readFile(new URL('../src/app.mjs',import.meta.url),'utf8')).replaceAll('\r\n','\n');
const marker = "app.addEventListener('click', event => {\n  const target = event.target.closest?.('[data-action]');\n  const action = target?.dataset.action;\n  const actions = ['generate-reminder-draft'";
const start=source.indexOf(marker),end=source.indexOf('}, true);',start)+'}, true);'.length;
assert.ok(start>=0&&end>start);
const settle=()=>new Promise(resolve=>setImmediate(resolve));
async function clickPermission(permission,{changeOwner=false}={}) {
 const owner={reminderNotificationPermission:'default',backgroundRuntime:{desiredEnabled:false},status:'',error:''};
 const other={status:'another route',error:''};let listener;
 const context=vm.createContext({state:owner,native:true,app:{addEventListener(_name,callback){listener=callback;}},render(){},
  requestReminderNotificationPermission:async()=>{owner.reminderNotificationPermission=permission;if(changeOwner)context.state=other;return ['granted','legacy'].includes(permission);},loadDesktopBackgroundRuntime:async()=>owner.backgroundRuntime});
 vm.runInContext(source.slice(start,end),context);
 listener({target:{closest:()=>({dataset:{action:'request-reminder-notification-permission'}})},preventDefault(){},stopImmediatePropagation(){}});
 await settle();assert.equal(owner.error,'');return {owner,other};
}
test('unreadable permission is not displayed as permission not granted in the reminder page',()=>{
 const html=renderDesktopRemindersPage({native:true,notificationEnabled:true,notificationPermission:'unavailable'});
 assert.ok(html.includes('无法确认系统通知权限'),'Unknown permission must be explained');
 assert.ok(!html.includes('系统通知尚未授权'),'Unknown is not a denial');
});
test('unreadable settings permission offers retry rather than claiming an authorization prompt',()=>{
 const html=renderAndroidSettingsShell({page:'reminders',capabilities:{monitorNotifications:true},reminderNotificationPermission:'unavailable'});
 assert.ok(html.includes('点按重试'),'Unavailable status should permit another read');
 assert.ok(!html.includes('点按授权'),'Unavailable status is not pending authorization');
});
test('actual permission action reports unknown state without claiming system refusal',async()=>{
 const {owner}=await clickPermission('unavailable');assert.ok(owner.status.includes('无法确认系统通知权限'));assert.ok(!owner.status.includes('未授予'));
});
test('known denial and granted permission retain distinct action results',async()=>{
 const denied=await clickPermission('denied');assert.ok(denied.owner.status.includes('未授予'));
 const granted=await clickPermission('granted');assert.ok(granted.owner.status.includes('已授予'));
});
test('disabled notification keeps local results wording even if permission is unreadable',()=>{
 const html=renderDesktopRemindersPage({notificationEnabled:false,notificationPermission:'unavailable'});assert.ok(html.includes('结果只保存在本机'));
});
test('late permission action does not change another route status',async()=>{
 const {other}=await clickPermission('unavailable',{changeOwner:true});assert.equal(other.status,'another route');assert.equal(other.error,'');
});
