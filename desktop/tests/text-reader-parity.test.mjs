import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { renderSafeMarkdown } from '../src/safe-markdown.mjs';
import { icon, icons } from '../src/icon-source.mjs';
const source=readFileSync(new URL('../src/app.mjs',import.meta.url),'utf8');
const a=source.indexOf('function textPreviewDialog()'),b=source.indexOf('\nfunction ',a+1);
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function render(preview) {
 const context=vm.createContext({state:{textPreview:preview},escape,bytes:value=>`${value} B`,renderSafeMarkdown,icon:(path,label)=>`<svg><title>${label}</title><path d="${path}"></path></svg>`,icons:vm.runInNewContext(source.match(/^const icons = .*;$/m)[0]+";icons"),sharedIcon:icon,sharedIcons:icons});
 vm.runInContext(source.slice(a,b),context);return context.textPreviewDialog();
}
const payload={workspaceId:'w',attachmentId:'a',displayName:'报告.md',mimeType:'text/markdown',byteCount:42,text:'正文 [链接](https://example.invalid)'};
test('reader header has one filename between back and menu; text actions retain original preview routes',()=>{
 const html=render(payload);assert.doesNotMatch(html,/undefined|<path d="</);assert.match(html,/class="text-preview-title">报告.md/);assert.match(html,/data-action="close-text-preview"/);
 for(const action of ['save-preview-attachment','share-preview-attachment','copy-text-preview'])assert.match(html,new RegExp(`class="text-preview-menu-item" data-action="${action}" data-preview-kind="text"`));
 assert.match(html,/<span>下载文件<\/span>/);assert.match(html,/<span>分享文件<\/span>/);assert.match(html,/<span>复制文本<\/span>/);
 assert.match(html,/<h1>报告<\/h1>/);assert.doesNotMatch(html,/<a\b|data-copy=|chat-source-shortcut/);
});
test('existing markdown title stays inert and does not gain a duplicate filename title',()=>{
 const html=render({...payload,text:'# <标题>\n\n```js\n<script>alert(1)</script>\n```'});
 assert.doesNotMatch(html,/<h1>报告<\/h1>|<script>|data-action="copy-markdown-block"/);assert.match(html,/&lt;script&gt;/);
});
test('loading/error keeps actions disabled and filenames/errors cannot inject markup',()=>{
 const html=render({...payload,displayName:'<img>.md',error:'<script>失败</script>'});
 assert.equal((html.match(/class="text-preview-menu-item"[^>]* disabled/g)||[]).length,3);assert.doesNotMatch(html,/<img|<script>/);assert.match(html,/&lt;img&gt;.md/);
 assert.equal((render({...payload,loading:true}).match(/class="text-preview-menu-item"[^>]* disabled/g)||[]).length,3);
});
test('truncated facts remain explicit; dotfile title never becomes empty',()=>{
 const html=render({...payload,displayName:'.env',truncated:true});assert.match(html,/<h1>.env<\/h1>/);assert.match(html,/仅显示前 128 KiB/);
});

const officeFormats=[['docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document'],['xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],['pptx','application/vnd.openxmlformats-officedocument.presentationml.presentation']];
for(const [ext,mimeType] of officeFormats)for(const generic of [false,true])test(`Office ${ext} ${generic?'generic fallback':'exact MIME'} reader preserves literal extracted characters`,()=>{
 const text='# 原文标题\n_x0041_ **字面星号** [字面链接](https://example.invalid) <script>literal</script>\n甲\t乙';
 const html=render({...payload,displayName:`报告.${ext}`,mimeType:generic?'application/octet-stream':mimeType,text});
 assert.ok(html.includes(`<pre>${escape(text)}</pre>`),'document characters must remain literal, not Markdown formatting');
 assert.match(html,/<h1>报告<\/h1>/);assert.doesNotMatch(html,/<em>|<strong>|<a\b|<script>/);assert.match(html,/aria-label="文档原文"/);
});
test('explicit Markdown MIME retains existing inert formatting even with an Office-looking display name',()=>{
 const html=render({...payload,displayName:'报告.xlsx',text:'# 原Markdown标题\n\n**强调** _斜体_'});
 assert.match(html,/<strong>强调<\/strong>/);assert.match(html,/<em>斜体<\/em>/);assert.doesNotMatch(html,/<h1>报告<\/h1>|aria-label="文档原文"/);
});
test('an executable suffix is never interpreted as an Office document by filename fallback',()=>{
 const html=render({...payload,displayName:'报告.xlsx.exe',mimeType:'application/octet-stream',text:'**原有格式**'});
 assert.match(html,/<strong>原有格式<\/strong>/);assert.doesNotMatch(html,/aria-label="文档原文"/);
});

for(const [ext,mimeType] of [['xml','application/xml'],['xml','text/xml'],['yaml','application/x-yaml'],['yaml','text/yaml'],['html','text/html']])test('source reader preserves exact literal text '+mimeType,()=>{
 const text='# 原样注释\nroot:\n  enabled: true\n  stars: **字面文本**\n<script>globalThis.sourcePreviewExecuted=true</script>\n<img src="https://example.invalid/should-not-load">\n[原样链接](https://example.invalid)';
 const html=render({...payload,displayName:`源文件.${ext}`,mimeType,text});assert.ok(html.includes(`<pre>${escape(text)}</pre>`));assert.match(html,/<h1>源文件<\/h1>/);assert.match(html,/aria-label="文件原文"/);assert.doesNotMatch(html,/<script|<img|<iframe|<a\b|<strong>/);
});
