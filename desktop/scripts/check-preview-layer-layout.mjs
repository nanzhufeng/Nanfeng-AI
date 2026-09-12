import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const source = readFileSync(new URL('../src/app.mjs', import.meta.url), 'utf8');
const css = ['styles.css', 'chat-shell.css', 'image-preview.css'].map(name => readFileSync(new URL(`../src/${name}`, import.meta.url), 'utf8')).join('\n');
const modalOwner = readFileSync(new URL('../src/modal-layer-owner.mjs', import.meta.url), 'utf8').replaceAll('export function', 'function');
const escape = value => String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
const names = ['image','pdf','video','audio','text'];
const render = (kind, data) => {
  const name = kind === 'boundary' ? 'previewBoundaryDialog' : `${kind}PreviewDialog`;
  const start = source.indexOf(`function ${name}()`);
  const end = source.indexOf('\nfunction ', start + 10);
  return new Function('state','escape','icon','icons','bytes','formatDuration', source.slice(start,end) + `;return ${name}();`)(
    {[kind === 'boundary' ? 'previewBoundary' : `${kind}Preview`]:data}, escape,
    () => '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M6 18L18 6"/></svg>', {close:''}, n=>`${n} B`,()=> '0:01');
};
const browser = await chromium.launch({headless:true, ...(process.env.CHROME_EXECUTABLE ? {executablePath:process.env.CHROME_EXECUTABLE} : {})});
const results=[];
try {
  const page=await browser.newPage();
  for (const viewport of [{width:1280,height:800},{width:640,height:480}]) {
    await page.setViewportSize(viewport);
    for (const kind of [...names,'boundary']) for (const phase of ['ready','loading','error']) {
      const svg='<svg xmlns="http://www.w3.org/2000/svg" width="300" height="1600"><rect width="300" height="1600" fill="orange"/><text x="10" y="1580">BOTTOM</text></svg>';
      const data={displayName:'local-fixture',width:300,height:1600,byteCount:123,pageNumber:1,pageCount:2,mimeType:'text/plain',text:'完整文本\n'.repeat(500),reason:'unsupported fixture',dataUrl:`data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`,objectUrl:'',loading:phase==='loading',error:phase==='error'?'read failure':null};
      if (process.env.PREVIEW_MEDIA_DIR && ['video','audio','pdf'].includes(kind)) {
        const [file,mime] = ({video:['video.mp4','video/mp4'],audio:['audio.wav','audio/wav'],pdf:['page.png','image/png']})[kind];
        const encoded = `data:${mime};base64,${readFileSync(`${process.env.PREVIEW_MEDIA_DIR}/${file}`).toString('base64')}`;
        data.dataUrl=encoded;data.objectUrl=encoded;data.mimeType=mime; if (kind==='pdf') data.pageCount=1;
      }
      if (process.env.PREVIEW_FIXTURE_DIR && phase === 'ready' && viewport.width === 1280) {
        mkdirSync(process.env.PREVIEW_FIXTURE_DIR,{recursive:true});
        writeFileSync(`${process.env.PREVIEW_FIXTURE_DIR}/${kind}.html`, `<style>${css}</style><div class="app-shell chat-first" id="app"><button class="chat-sidebar-divider" style="position:fixed;left:260px;top:0;height:100vh"></button>${render(kind,data)}</div>`);
      }
      await page.goto('about:blank');
      await page.setContent(`<style>${css}</style><div class="app-shell chat-first" id="app"><button id="background" data-action="background">background</button><button class="chat-sidebar-divider" style="position:fixed;left:260px;top:0;height:100vh"></button>${render(kind,data)}</div>`);
      await page.addScriptTag({content:modalOwner+';installModalLayerOwner(document.querySelector("#app"));document.addEventListener("click", e=>{if(e.target.closest("[data-action^=close-]"))e.target.closest(".scrim").remove()});'});
      if (phase === 'ready' && ['image','pdf'].includes(kind)) await page.waitForFunction(() => [...document.querySelectorAll('.image-preview-dialog img')].every(img => img.complete && img.naturalWidth > 0));
      const metrics=await page.evaluate(()=>{
        const dialog=document.querySelector('.image-preview-dialog'); const r=dialog.getBoundingClientRect();
        const body=document.querySelector('.image-preview-viewport,.pdf-preview-frame,.video-preview-frame,.local-text-preview');
        const b=body?.getBoundingClientRect();const close=dialog.querySelector('.close').getBoundingClientRect();
        return {width:r.width,height:r.height,x:r.x,y:r.y,body:b&&{top:b.top,bottom:b.bottom,height:b.height},closeVisible:close.top>=0&&close.bottom<=innerHeight,overlayOnTop:!!document.elementFromPoint(262,innerHeight/2)?.closest('.attachment-preview-overlay'),backgroundInert:document.querySelector('#background').inert,overflow:dialog.scrollHeight>dialog.clientHeight+1};
      });
      assert.equal(metrics.width,viewport.width);assert.equal(metrics.height,viewport.height);assert.equal(metrics.x,0);assert.equal(metrics.y,0);
      assert.ok(metrics.overlayOnTop&&metrics.backgroundInert&&metrics.closeVisible&&!metrics.overflow, JSON.stringify({kind,phase,metrics}));
      if(metrics.body) assert.ok(metrics.body.height>0&&metrics.body.top>=0&&metrics.body.bottom<=viewport.height);
      if (process.env.PREVIEW_SCREENSHOT && kind === 'image' && phase === 'ready' && viewport.width === 1280) await page.screenshot({path:process.env.PREVIEW_SCREENSHOT});
      await page.keyboard.press('Shift+Tab');assert.ok(await page.evaluate(()=>!!document.activeElement.closest('.scrim')));
      await page.keyboard.press('Escape');assert.equal(await page.locator('.scrim').count(),0,`${kind}/${phase}`);
      assert.equal(await page.locator('#background').evaluate(el=>el.inert),false);
      results.push({kind,phase,...viewport,passed:true});
    }
  }
  await page.goto('about:blank');
  await page.setViewportSize({width:1280,height:800});
  await page.setContent(`<style>${css}</style><div class="app-shell chat-first" id="app"><button class="chat-sidebar-divider" style="position:fixed;left:260px;top:0;height:100vh"></button><footer class="chat-sidebar-footer" style="position:fixed;left:0;top:200px;width:350px;height:200px"></footer><div class="chat-context-menu" data-positioned="true" style="left:240px;top:200px;height:200px">menu</div></div>`);
  assert.equal(await page.evaluate(()=>document.elementFromPoint(262,250).className),'chat-context-menu');
  results.push({kind:'context-menu-over-divider-and-footer',passed:true});
  await page.evaluate(()=>document.querySelector('#app').insertAdjacentHTML('beforeend','<main id="settings"><button id="behind-picker">behind</button><div class="android-settings-picker-scrim" data-action="dismiss-settings-picker"><section role="dialog"><button>pick</button></section></div></main>'));
  await page.addScriptTag({content:modalOwner+';installModalLayerOwner(document.querySelector("#app"));document.addEventListener("click",e=>{if(e.target.matches("[data-action=dismiss-settings-picker]"))e.target.remove()});'});
  assert.ok(await page.locator('#behind-picker').evaluate(el=>el.inert));
  assert.ok(await page.evaluate(()=>!!document.elementFromPoint(262,250).closest('.android-settings-picker-scrim')));
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('.android-settings-picker-scrim').count(),0);
  assert.equal(await page.locator('#behind-picker').evaluate(el=>el.inert),false);
  results.push({kind:'nested-settings-picker-input-boundary',passed:true});
  await page.goto('about:blank');
  await page.setContent(`<style>${css}</style><div id="app"><section style="position:fixed;right:0;bottom:0;width:320px;height:100px;overflow:hidden"><details class="conversation-lifecycle-actions"><summary>menu</summary><span><button>one</button><button>two</button><button>three</button></span></details></section></div>`);
  await page.addScriptTag({content:modalOwner+';installActionMenuLayerOwner(document.querySelector("#app"));'});
  await page.locator('summary').click();
  await page.waitForFunction(()=>document.querySelector('[popover]')?.matches(':popover-open'));
  const popup=await page.locator('[popover]').evaluate(el=>{const r=el.getBoundingClientRect();return {visible:document.elementFromPoint(r.x+10,r.y+10)===el||el.contains(document.elementFromPoint(r.x+10,r.y+10)),top:r.top,bottom:r.bottom}});
  assert.ok(popup.visible&&popup.top>=8&&popup.bottom<=792,JSON.stringify(popup));
  results.push({kind:'bottom-action-menu-escapes-scroll-clipping',passed:true});
  // Persist a native-WebKit fixture with the same popover owner and clipped parent.
  if (process.env.PREVIEW_FIXTURE_DIR) writeFileSync(`${process.env.PREVIEW_FIXTURE_DIR}/menu.html`, `<style>${css}</style><div id="app"><section style="position:fixed;right:0;bottom:0;width:320px;height:100px;overflow:hidden"><details class="conversation-lifecycle-actions"><summary>menu</summary><span><button>one</button><button>two</button><button>three</button></span></details></section></div><script>${modalOwner};installActionMenuLayerOwner(document.querySelector('#app'));document.querySelector('details').open=true;</script>`);
  console.log(JSON.stringify({checks:results.length,results},null,2));
} finally {await browser.close();}
