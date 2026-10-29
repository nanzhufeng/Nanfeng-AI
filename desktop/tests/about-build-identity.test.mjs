import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
const data={summary:{id:'synthetic'},exchange:{conversations:[],projects:[],knowledge:[],memory:[],relations:[]}};
const commit='2873f051cbea91c0122f14a08f807f374b28da75',fingerprint='0123456789abcdef'.repeat(4);
const render=(platform='windows',extra={},page='about')=>renderChatFirstShell({data,native:true,pane:'settings',settingsSection:page,runtimeInfo:{platform,version:'1.0.23',gitCommit:commit,gitWorktreeDirty:true,sourceInputSha256:fingerprint,sourceInputFileCount:321,...extra}});
test('Windows and Mac About link version history, repository and exact commit baseline separately from current WIP input',()=>{
 for(const platform of ['windows','macos']){const html=render(platform);for(const url of ['https://github.com/nanzhufeng/Nanfeng-AI','https://github.com/nanzhufeng/Nanfeng-AI/releases',`https://github.com/nanzhufeng/Nanfeng-AI/commit/${commit}`])assert.ok(html.includes(`href="${url}"`),url);
 assert.ok(html.includes('构建时间'));assert.match(html,/aria-label="Desktop 版 1\.0\.23[^"]*默认浏览器/);assert.match(html,/aria-label="Git Commit：2873f051[^"]*默认浏览器/);
 assert.ok(html.includes('源码输入'));assert.ok(html.includes(fingerprint.slice(0,16)));assert.ok(html.includes('含未提交改动'));assert.ok(html.includes('提交基线'));assert.ok(!html.includes('/releases/tag/'),'local dirty build must not pretend a published tag exists');
 for(const anchor of html.matchAll(/<a\b[^>]*data-action="open-source-link"[^>]*>/g))assert.match(anchor[0],/aria-label=/);
 }
});
test('missing or malformed build identity stays unavailable without inventing a commit or fingerprint link',()=>{
 const html=render('windows',{gitCommit:'a'.repeat(41),sourceInputSha256:'<script>bad</script>'});assert.ok(!html.includes('/commit/'));assert.ok(html.includes('源码输入：未记录'));assert.ok(!html.includes('<script>'));
});
test('feature review explains background title and attachment search through existing approved entries on both platforms',()=>{
 for(const platform of ['windows','macos']){const html=render(platform,{},'feature-review');for(const text of ['后台标题整理','不等待标题','手动标题优先','联网附件核对','已检索不等于已核实'])assert.ok(html.includes(text),`${platform}: ${text}`);}
});
