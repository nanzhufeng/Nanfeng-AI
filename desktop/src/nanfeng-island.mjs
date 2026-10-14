/** Nanfeng Island: background-only generation metadata presentation. */
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const isRunning = task => ['PENDING', 'RUNNING'].includes(task.state);
export const isComplete = task => ['RESPONSE_COMMITTED', 'COMPLETED', 'COMPLETED_ACCOUNTING_PENDING'].includes(task.state);
export function taskStage(task) {
  return isRunning(task) ? (task.savedCharacters > 0 ? '正在生成' : '等待模型输出') : isComplete(task) ? '回复已完成' : task.state === 'CANCELLED' ? '已停止生成' : '生成中断';
}
export function taskDetail(task, now) {
  const seconds = Math.max(0, Math.floor(((isRunning(task) ? now : task.updatedAt) - task.startedAt) / 1000));
  return `已保存 ${task.savedCharacters || 0} 字 · ${seconds} 秒`;
}
export class IslandPresentation {
  constructor() { this.expanded = false; this.tasks = []; this.notice = null; this.deadlines = new Map(); this.dismissed = new Set(); this.expandedKeys = new Set(); }
  update(tasks, now) {
    this.tasks = Array.isArray(tasks) ? tasks : [];
    if (this.expanded && !this.notice && !this.tasks.some(task => this.expandedKeys.has(task.key))) { this.expanded=false;this.expandedKeys.clear(); }
    for (const task of this.tasks) if (!isRunning(task) && !this.deadlines.has(task.key)) {
      const deadline = isComplete(task) ? (now-task.updatedAt < 8000 ? now+2600 : 0) : Math.min(now+3000,task.updatedAt+3000);
      this.deadlines.set(task.key,deadline);
    }
    const keys = new Set(this.tasks.map(t => t.key));
    for (const key of this.deadlines.keys()) if (!keys.has(key)) this.deadlines.delete(key);
    for (const key of this.dismissed) if (!keys.has(key)) this.dismissed.delete(key);
  }
  feedback(message, kind, now) { this.notice = message ? {message,kind,until:now+(kind==='error'?8000:2600)} : null; }
  toggle(now) {
    const opening=!this.expanded;
    const shown=this.visible(now);
    if(opening) this.expandedKeys=new Set(shown.map(task=>task.key));
    else {
      for (const task of shown) if (isComplete(task)) this.deadlines.set(task.key,now+2600);
      if (this.notice) this.notice.until=now+(this.notice.kind==='error'?8000:2600);
    }
    this.expanded=opening && Boolean(shown.length || this.notice);
  }
  collapse(now) { if (this.expanded) this.toggle(now); }
  dismiss() { for (const task of this.tasks) if (!isRunning(task)) this.dismissed.add(task.key);this.notice=null;this.expanded=false; }
  visible(now) {
    if (this.notice && !this.expanded && now >= this.notice.until) this.notice = null;
    const visible = this.tasks.filter(task => !this.dismissed.has(task.key) && (isRunning(task) || (isComplete(task) && this.expanded && this.expandedKeys.has(task.key)) || now < (this.deadlines.get(task.key) || 0)));
    if (!visible.length && !this.notice) {this.expanded=false;this.expandedKeys.clear();}
    return visible;
  }
}
export function islandMarkup(controller, now) {
  const tasks=controller.visible(now);const active=tasks.filter(isRunning);const selected=active[0] || tasks.at(-1);
  if (!selected && !controller.notice) return '';
  const kind=controller.notice?.kind || (selected && isRunning(selected) ? 'running' : selected && isComplete(selected) ? 'success' : selected?.state==='CANCELLED' ? 'cancelled' : 'error');
  const summary=controller.notice?.message.split('\n')[0] || (active.length>1 ? `${active.length} 个对话生成中` : `${selected.modelLabel || "模型"} · ${taskStage(selected)} · ${selected.savedCharacters || 0} 字`);
  return `<section class="nanfeng-island ${kind}${controller.expanded?' expanded':''}" aria-label="南枫岛"><button class="island-summary" data-island-action="toggle" aria-expanded="${controller.expanded}"><span class="island-indicator" aria-hidden="true">${kind==='success'?'✓':kind==='error'?'!':kind==='cancelled'?'−':''}</span><span data-island-summary>${escape(summary)}</span></button><span class="island-sr-status" role="status" aria-live="polite">${escape(controller.notice?.message.split("\n")[0] || (active.length>1 ? `${active.length} 个对话生成中` : taskStage(selected)))}</span>${controller.expanded?`<div class="island-details">${controller.notice?`<p>${escape(controller.notice.message)}</p>`:''}${tasks.map(task=>`<article data-island-task="${escape(task.key)}"><strong>${escape(task.modelLabel || '模型连接中')}</strong><p data-island-detail>${escape(taskStage(task))} · ${escape(taskDetail(task,now))}</p>${!isRunning(task)&&!isComplete(task)&&task.state!=='CANCELLED'?'<p>断点已保留，请返回对话查看原因并手动续写。</p>':''}<div class="island-actions"><button data-island-action="open" data-island-key="${escape(task.key)}">查看对话</button>${isRunning(task)?`<button data-island-action="stop" data-island-key="${escape(task.key)}">停止</button>`:''}</div></article>`).join('')}</div>${active.length?'':'<div class="island-actions"><button data-island-action="dismiss">关闭提醒</button></div>'}`:''}</section>`;
}
export function mountNanfengIsland({host, invoke, background=false, clock=()=>Date.now(), onError=()=>{}}) {
  // Only the native background window may present generation islands.
  if (!background) {
    host.innerHTML = '';
    return { feedback() {}, update() {}, dispose() { host.innerHTML = ''; } };
  }
  const controller=new IslandPresentation();let disposed=false;let timer=null;let last='';let expanded=false;let structure='';
  function paint() {
    if(disposed) return;
    const now=clock();const visible=controller.visible(now);const active=visible.filter(isRunning);const selected=active[0] || visible.at(-1);
    const markup=islandMarkup(controller,now);
    const signature=JSON.stringify({expanded:controller.expanded,notice:controller.notice?.message,kind:controller.notice?.kind,tasks:visible.map(t=>[t.key,t.state,t.modelLabel])});
    if(signature===structure && host.firstElementChild) {
      const summary=controller.notice?.message.split('\n')[0] || (active.length>1 ? `${active.length} 个对话生成中` : `${selected.modelLabel || "模型"} · ${taskStage(selected)} · ${selected.savedCharacters || 0} 字`);
      host.querySelector('[data-island-summary]').textContent=summary;
      const live=host.querySelector('.island-sr-status');const announcement=controller.notice?.message.split('\n')[0] || (active.length>1 ? `${active.length} 个对话生成中` : taskStage(selected));if(live && live.textContent!==announcement) live.textContent=announcement;
      for(const article of host.querySelectorAll('[data-island-task]')) {
        const task=visible.find(t=>t.key===article.dataset.islandTask);
        if(task) article.querySelector('[data-island-detail]').textContent=`${taskStage(task)} · ${taskDetail(task,now)}`;
      }
      return; // Progress changes text alone; focus, pointer targets and scroll remain stable.
    }
    if(markup===last) return;
    const old=host.firstElementChild;const rect=old?.getBoundingClientRect();
    const animating=old?.getAnimations().some(a=>a.playState==='running');
    const focused=document.activeElement;
    const restore=host.contains(focused)?{action:focused.dataset.islandAction,key:focused.dataset.islandKey}:null;
    const scroll=host.querySelector('.island-details')?.scrollTop || 0;
    const modeChanged=expanded!==controller.expanded;
    host.innerHTML=markup;last=markup;structure=signature;expanded=controller.expanded;
    const details=host.querySelector('.island-details');if(details) details.scrollTop=scroll;
    if(restore) {const target=[...host.querySelectorAll('[data-island-action]')].find(b=>b.dataset.islandAction===restore.action && b.dataset.islandKey===restore.key) || host.querySelector('.island-summary');target?.focus({preventScroll:true});}
    const next=host.firstElementChild;
    if(next && (!old || modeChanged || animating) && !globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      const nextRect=next.getBoundingClientRect();
      next.animate([{transform:`scale(${rect?rect.width/nextRect.width:.9},${rect?rect.height/nextRect.height:.9})`},{transform:'scale(1.025,1.035)',offset:.78},{transform:'scale(1)'}],{duration:360,easing:'cubic-bezier(.2,.7,.2,1)'});
    }
  }

  async function poll() {
    if(disposed) return;
    try { if(invoke) controller.update(await invoke('read_generation_island'),clock());paint();if(background&&invoke) await invoke('generation_island_visibility',{visible:!!host.firstElementChild,height:(host.firstElementChild?.getBoundingClientRect().height || 36)+12}); }
    catch (error) {onError(error);} // An unavailable presentation cannot cancel native generation.
    finally {if(!disposed) timer=setTimeout(poll,1000);}
  }
  const collapse=()=>{controller.collapse(clock());paint();};
  const outside=event=>{if(controller.expanded && !host.contains(event.target)) collapse();};
  document.addEventListener('pointerdown',outside,true);
  let unlisten=null;
  const listen=globalThis.window?.__TAURI__?.event?.listen;
  if(background && listen) void listen('nanfeng-island-collapse-v1',collapse).then(fn=>{if(disposed) fn();else unlisten=fn;}).catch(onError);
  host.addEventListener('click',async event=>{
    const button=event.target.closest('[data-island-action]');if(!button) return;
    const action=button.dataset.islandAction;
    if(action==='toggle') controller.toggle(clock());
    else if(action==='dismiss') controller.dismiss();
    else if(invoke) {try {await invoke('generation_island_action',{key:button.dataset.islandKey,action});if(action==='open') controller.collapse(clock());} catch {controller.feedback('任务操作未完成，请返回对话重试。','error',clock());}}
    paint();
  });
  void poll();
  return {controller, feedback(message,kind='success') {controller.feedback(message,kind,clock());paint();}, update(tasks) {controller.update(tasks,clock());paint();}, dispose() {disposed=true;clearTimeout(timer);document.removeEventListener('pointerdown',outside,true);unlisten?.();host.innerHTML='';}};
}
