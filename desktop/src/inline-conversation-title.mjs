/** Presentation owner only; persistence remains in the existing conversation lifecycle. */
export function inlineTitleInput(edit) {
  const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  return `<span class="chat-inline-title-editor"><input id="conversation-inline-title" class="chat-inline-title-input" data-inline-conversation-rename aria-label="编辑会话标题" aria-describedby="conversation-inline-title-error" aria-invalid="${!!edit.error}" aria-busy="${!!edit.saving}" ${edit.saving?'readonly':''} autocomplete="off" value="${escape(edit.draft)}"><span id="conversation-inline-title-error" class="chat-inline-title-error" role="alert">${escape(edit.error||'')}</span></span>`;
}
export function createInlineTitleEditor({ host, document, getState, routeKey, navigationKey=routeKey, prepare, mutate, readCurrent, render, native, resolveHeader, defer=queueMicrotask }) {
  let serial=0, painting=false, repaintPending=false, pendingOpen=null;
  const selector='[data-inline-conversation-rename]';
  function current(edit=getState().inlineConversationRename) {
    const state=getState();
    return !!edit && state===edit.owner && state.inlineConversationRename===edit && routeKey()===edit.route
      && !state.batchEditing && !state.settingsConversationReturn?.readOnly && state.dialog===edit.dialog;
  }
  function reconcile() {
    const state=getState(),edit=state.inlineConversationRename;
    if(edit && !current(edit)) state.inlineConversationRename=null;
    return state.inlineConversationRename;
  }
  async function begin({ id, revision, workspaceId=null, title, surface='sidebar' }) {
    const owner=getState(),navigation=navigationKey(),dialog=owner.dialog;
    const active=owner.inlineConversationRename;
    if(current(active) && active.id===id && active.workspaceId===workspaceId && active.surface===surface) return;
    // A physical double-click emits both click(detail=2) and dblclick. Keep one
    // pending source preparation, without cancelling a different navigation.
    if(!owner.batchEditing && !owner.settingsConversationReturn?.readOnly
      && pendingOpen && pendingOpen.generation===serial && pendingOpen.owner===owner
      && pendingOpen.navigation===navigation && pendingOpen.dialog===dialog
      && pendingOpen.id===id && pendingOpen.workspaceId===workspaceId
      && pendingOpen.revision===Number(revision) && pendingOpen.surface===surface) return;
    const generation=++serial;
    const openingCurrent=()=>generation===serial && getState()===owner && navigationKey()===navigation
      && owner.dialog===dialog && !owner.batchEditing && !owner.settingsConversationReturn?.readOnly;
    if(!id || owner.batchEditing || owner.settingsConversationReturn?.readOnly) return;
    if(owner.inlineConversationRename?.saving) return;
    const opening={owner,navigation,dialog,id,workspaceId,revision:Number(revision),surface,generation};
    pendingOpen=opening;
    try {if(prepare && !await prepare({id,revision,workspaceId},{isCurrent:openingCurrent})) return;}
    catch(error) {
      if(openingCurrent()){owner.error=`标题编辑未打开：${String(error)}`;render();}
      return;
    }
    finally {if(pendingOpen===opening)pendingOpen=null;}
    if(!openingCurrent()) return;
    owner.contextMenu=null;
    const original=String(title || '未命名会话');
    const edit={id,revision:Number(revision),workspaceId,original,draft:original,surface,route:routeKey(),dialog:owner.dialog,error:'',saving:false,composing:false};
    Object.defineProperty(edit,'owner',{value:owner});
    owner.inlineConversationRename=edit;
    render({runtimeRefresh:true});
    defer(()=>{if(!current(edit))return;const input=host.querySelector(selector);input?.focus({preventScroll:true});input?.select();});
  }
  function cancel() {
    ++serial;pendingOpen=null;const state=getState();state.inlineConversationRename=null;
    render({runtimeRefresh:true});
  }
  async function presentFailure(edit,input,error) {
    if(!current(edit))return;
    const message=String(error || '标题未保存，请保留草稿后重试。');
    if(readCurrent && (edit.conflictPending || message.includes('REVISION_CONFLICT'))) {
      edit.conflictPending=true;edit.conflict=null;edit.readingConflict=true;
      try {
        const latest=await readCurrent({id:edit.id,workspaceId:edit.workspaceId},{isCurrent:()=>current(edit)});
        if(!current(edit))return;
        if(latest?.id===edit.id && Number.isSafeInteger(latest.revision) && latest.revision>0 && typeof latest.title==='string') {
          edit.conflict={revision:latest.revision,title:latest.title};
          edit.error=`会话已变化，当前标题为“${latest.title}”。草稿已保留，按 Enter 确认保存，Esc 取消。`;
        } else {edit.error='会话已移除或不可编辑，草稿已保留，请先退出标题编辑。';}
      } catch {
        if(!current(edit))return;
        edit.error='标题版本核对失败，草稿已保留；按 Enter 重新核对，Esc 取消。';
      }
    } else {edit.error=message;}
    if(!current(edit))return;
    edit.readingConflict=false;edit.saving=false;input.readOnly=false;
  }
  async function commit(input,{confirmConflict=false}={}) {
    const edit=reconcile();
    if(!edit || !input?.isConnected || !input.matches(selector) || edit.saving) return;
    edit.draft=input.value;
    if(edit.composing) {edit.blurPending=true;return;}
    if(edit.conflictPending) {
      if(!confirmConflict)return;
      if(!edit.conflict) {
        edit.saving=true;input.readOnly=true;
        await presentFailure(edit,input,'REVISION_CONFLICT');
        if(current(edit))render({runtimeRefresh:true});
        return;
      }
      edit.revision=edit.conflict.revision;edit.original=edit.conflict.title;
      edit.conflictPending=false;edit.conflict=null;
    }
    const title=edit.draft.trim();
    if(!title) {edit.error='会话标题不能为空。';render({runtimeRefresh:true});return;}
    if([...title].length>120) {edit.error='会话标题最多120个字。';render({runtimeRefresh:true});return;}
    if(title===edit.original.trim()) {cancel();return;}
    if(!native) {edit.error='Web 预览不能保存会话标题。';render({runtimeRefresh:true});return;}
    edit.saving=true;edit.error='';
    input.readOnly=true;input.setAttribute?.('aria-busy','true');
    // Do not remove/refocus the editing input when a write begins. Enter and
    // focusout share this gate, so one user confirmation issues one write.
    try {
      const result=await mutate({dataset:{id:edit.id,revision:String(edit.revision),...(edit.workspaceId?{workspaceId:edit.workspaceId}:{})}},'update',{title},'会话已重命名。',{presentationCurrent:()=>current(edit)});
      if(!current(edit)) return;
      if(result?.localSaved) edit.owner.inlineConversationRename=null;
      else {await presentFailure(edit,input,result?.error);if(!current(edit))return;}
    } catch(error) {
      if(!current(edit)) return;
      await presentFailure(edit,input,`标题未保存：${String(error)}`);if(!current(edit))return;
    }
    render({runtimeRefresh:true});
  }
  function onInput(event) {
    const edit=reconcile();if(!edit || !event.target.matches?.(selector))return;
    if(!edit.saving){edit.draft=event.target.value;if(!edit.conflictPending)edit.error='';}
  }
  function onKey(event) {
    const edit=reconcile();if(!edit || !event.target.matches?.(selector))return;
    if(event.isComposing || edit.composing || event.keyCode===229)return;
    if(!['Enter','Escape'].includes(event.key))return;
    // Some IMEs end composition before delivering the confirmation/cancel key.
    // Compare timestamps from this input's events, not a wall-clock or another edit.
    const imeTailAge=event.timeStamp-edit.compositionEndedAt;
    if(Number.isFinite(edit.compositionEndedAt) && Number.isFinite(event.timeStamp)
      && imeTailAge>=0 && imeTailAge<100){event.stopPropagation();return;}
    event.preventDefault();event.stopPropagation();
    if(event.key==='Escape') {if(!edit.saving || edit.readingConflict)cancel();}else void commit(event.target,{confirmConflict:true});
  }
  function onBlur(event) {if(!painting && event.target.matches?.(selector))void commit(event.target);}
  function onComposition(event) {
    const edit=reconcile();if(!edit || !event.target.matches?.(selector))return;
    edit.composing=event.type==='compositionstart';edit.draft=event.target.value;
    edit.compositionEndedAt=!edit.composing && Number.isFinite(event.timeStamp)?event.timeStamp:undefined;
    if(!edit.composing)defer(()=>{
      if(!current(edit))return;
      if(edit.blurPending){edit.blurPending=false;void commit(event.target);}
      else if(repaintPending){repaintPending=false;render({runtimeRefresh:true});}
    });
  }
  function prepareRender() {
    const edit=reconcile(),input=document.activeElement;
    if(!edit || !input?.matches?.(selector))return false;
    if(edit.composing && input.isConnected){repaintPending=true;return true;}
    const selection=[input.selectionStart,input.selectionEnd,input.selectionDirection];
    painting=true;
    defer(()=>{
      painting=false;if(!current(edit))return;
      const next=host.querySelector(selector);
      if(next && next!==input){next.focus({preventScroll:true});if(Number.isInteger(selection[0]))next.setSelectionRange(...selection);}
    });
    return false;
  }
  function fromTarget(target) {
    if(target.matches?.(selector))return null;
    const title=target.closest?.('.chat-history-title');
    if(title){const row=title.closest('[data-conversation-row]');if(!row || row.dataset.archived==='true')return null;return {id:row.dataset.id,revision:row.dataset.revision,workspaceId:row.dataset.workspaceId||null,title:title.textContent,surface:'sidebar'};}
    return target.closest?.('.chat-title h1') ? resolveHeader() : null;
  }
  const doubleClick=event=>{
    if(event.type==='click' && event.detail<2)return;
    const target=fromTarget(event.target);if(!target)return;
    event.preventDefault();event.stopImmediatePropagation();
    const owner=getState(),navigation=navigationKey(),dialog=owner.dialog;
    const opening=begin(target),generation=serial;
    void opening.catch(error=>{
      if(generation!==serial || getState()!==owner || navigationKey()!==navigation || owner.dialog!==dialog)return;
      owner.error=`标题编辑未打开：${String(error)}`;render();
    });
  };
  for(const name of ['click','dblclick'])host.addEventListener(name,doubleClick,true);
  for(const [name,handler] of [['input',onInput],['keydown',onKey],['focusout',onBlur],['compositionstart',onComposition],['compositionend',onComposition]])host.addEventListener(name,handler,true);
  return {begin,cancel,commit,prepareRender,reconcile};
}
